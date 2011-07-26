package adaptorlib;

import mx.bigdata.jcalais.CalaisClient;
import mx.bigdata.jcalais.CalaisConfig;
import mx.bigdata.jcalais.CalaisObject;
import mx.bigdata.jcalais.CalaisResponse;
import mx.bigdata.jcalais.rest.CalaisRestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Map;

/**
 * This transform sends the content to the OpenCalais webservice, which
 * extracts named entities. We then inject this info as metadata.
 * We currently make the assumption that the incoming content is HTML.
 */
public class CalaisNERTransform extends DocumentTransform {

  public CalaisNERTransform() {
    super("CalaisNERTransform");
  }

  /**
   * This transform must take in a parameter of the form:
   * <code>(OpenCalaisAPIKey, key)</code>
   *
   * Optionally, extra parameters can be passed in to set which entity types to detect.
   * <code>(UseCalaisEntity:&amp;type&amp;, &quot;True&quot;|&quot;False&quot;)</code>
   * Valid types are:
   * <ul>
   * <li>All
   * <li>Company
   * <li>Country
   * <li>EmailAddress
   * <li>Facility
   * <li>Holiday
   * <li>IndustryTerm
   * <li>MedicalCondition
   * <li>Movie
   * <li>MusicAlbum
   * <li>MusicGroup
   * <li>Organization
   * <li>Person
   * <li>PhoneNumber
   * <li>Position
   * <li>Product
   * <li>ProvinceOrState
   * <li>PublishedMedium
   * <li>Region
   * <li>Technology
   * </ul>
   */
  @Override
  public void transform(ByteArrayOutputStream contentIn, ByteArrayOutputStream metadataIn,
                        OutputStream contentOut, OutputStream metadataOut,
                        Map<String, String> params) throws TransformException, IOException {
    String apiKey = params.get("OpenCalaisApiKey");
    if (apiKey == null) {
      throw new IllegalArgumentException("No api key given. Please set param: OpenCalaisApiKey");
    }
    boolean includeAllEntities = true;
    if ("False".equals(params.get("UseCalaisEntity:All"))) {
      includeAllEntities = false;
    }
    CalaisClient calaisClient = new CalaisRestClient(apiKey);
    CalaisConfig config = new CalaisConfig();
    config.set(CalaisConfig.ProcessingParam.CONTENT_TYPE, "TEXT/HTML");
    String content = contentIn.toString("UTF-8");

    CalaisResponse response = calaisClient.analyze(content, config);
    StringBuilder sb = new StringBuilder();
    for (CalaisObject entity : response.getEntities()) {
      String entityType = entity.getField("_type");
      String entityName = entity.getField("name");
      String entityParamKey = "UseCalaisEntity:"+ entityType;
      boolean shouldInclude = params.containsKey(entityParamKey) ?
          "True".equals(params.get(entityParamKey)) : includeAllEntities;
      if (shouldInclude) {
        sb.append(MessageFormat.format("<meta name=\"{0}\" content=\"{1}\" />\n",
                                       entityType, entityName));
      }
    }
    // This is a very simple insertion mechanism. It looks for the closing
    // </HEAD> element and inserts the metadata right before it.
    content = content.replaceFirst("</(HEAD|head)", "\n" + sb.toString() + "</HEAD");
    contentOut.write(content.getBytes());
    metadataIn.writeTo(metadataOut);
  }
}