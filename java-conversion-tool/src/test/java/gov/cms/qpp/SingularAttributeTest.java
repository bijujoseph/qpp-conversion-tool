package gov.cms.qpp;


import com.sun.org.apache.xerces.internal.dom.DeferredAttrNSImpl;
import com.sun.org.apache.xerces.internal.dom.DeferredElementNSImpl;
import gov.cms.qpp.conversion.Converter;
import gov.cms.qpp.conversion.correlation.PathCorrelator;
import gov.cms.qpp.conversion.correlation.model.Goods;
import gov.cms.qpp.conversion.decode.ClinicalDocumentDecoder;
import gov.cms.qpp.conversion.decode.MeasureDataDecoder;
import gov.cms.qpp.conversion.decode.MultipleTinsDecoder;
import gov.cms.qpp.conversion.decode.ReportingParametersActDecoder;
import gov.cms.qpp.conversion.model.TemplateId;
import gov.cms.qpp.conversion.model.error.AllErrors;
import gov.cms.qpp.conversion.model.error.Detail;
import gov.cms.qpp.conversion.model.error.TransformException;
import gov.cms.qpp.conversion.validate.ClinicalDocumentValidator;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static gov.cms.qpp.conversion.model.error.ValidationErrorMatcher.hasValidationErrorsIgnoringPath;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class SingularAttributeTest {

	private static final String NAMESPACE_URI = "urn:hl7-org:v3";
	private static Map<String, Goods> corrMap;
	private static DocumentBuilderFactory dbf;
	private static Set<String> exclusions;
	private static int inclusionCount = 0;

	private TransformerFactory tf = TransformerFactory.newInstance();
	private XPathFactory xpf = XPathFactory.newInstance();

	@BeforeClass
	@SuppressWarnings("unchecked")
	public static void before() throws NoSuchFieldException, IllegalAccessException {
		dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);

		Field corrMapField = PathCorrelator.class.getDeclaredField("pathCorrelationMap");
		corrMapField.setAccessible(true);
		corrMap = (Map<String, Goods>) corrMapField.get(null);

		exclusions = new HashSet<>(
			Arrays.asList(
				//MultipleTinsDecoder maps multiple tin/npi combination
				MultipleTinsDecoder.TAX_PAYER_IDENTIFICATION_NUMBER,
				MultipleTinsDecoder.NATIONAL_PROVIDER_IDENTIFIER,
				//There are no validations currently for entity type
				ClinicalDocumentDecoder.ENTITY_ID,
				//There are no validations currently for performanceEnd / performanceStart
				ReportingParametersActDecoder.PERFORMANCE_END,
				ReportingParametersActDecoder.PERFORMANCE_START,
				//stratum is not currently mapped
				"stratum",
				//Honestly not sure what to make of this
				MeasureDataDecoder.MEASURE_POPULATION)
		);

		corrMap.keySet().forEach(key -> {
			String[] components = key.split(PathCorrelator.KEY_DELIMITER);
			if (!exclusions.contains(components[1])){
				inclusionCount++;
			}
		});
	}

	//TODO: look into ENTITY_TYPE w/ multiple tin example
	//TODO: Exempt
	// MultipleTinsDecoder TAX_PAYER_IDENTIFICATION_NUMBER
	// MultipleTinsDecoder.NATIONAL_PROVIDER_IDENTIFIER
	// due to ugliness with multiple tin decoding

	@Test
	public void blanketDoubleUp() {
		assertThat("failed duplication scenarios should equal the inclusion count",
				blanketCheck(true), is(inclusionCount));
	}

	@Test
	public void blanketRemoval() {
		assertThat("failed removal scenarios should equal the inclusion count",
				blanketCheck(false), is(inclusionCount));
	}

	private int blanketCheck(boolean doubleUp) {
		int errorCount = 0;
		for (String key : corrMap.keySet()) {
			String[] components = key.split(PathCorrelator.KEY_DELIMITER);
			if (!exclusions.contains(components[1])) {
				List<Detail> details = executeScenario(components[0], components[1], doubleUp);

				if (!details.isEmpty()) {
					errorCount++;
				}
				assertThat("Combination of: " + components[0] + " and " +
					components[1] + " should be unique.",
					details.size(), greaterThan(0));
			}
		}
		return errorCount;
	}

	@Test
	public void doubleUpProgramName() {
		List<Detail> details = executeScenario(TemplateId.CLINICAL_DOCUMENT.name(),
				ClinicalDocumentDecoder.PROGRAM_NAME, false);

		assertThat("error should be about missing missing program name", details,
				hasValidationErrorsIgnoringPath(
						ClinicalDocumentValidator.CONTAINS_PROGRAM_NAME));
	}

	@Test
	public void noProgramName() {
		List<Detail> details = executeScenario(TemplateId.CLINICAL_DOCUMENT.name(),
				ClinicalDocumentDecoder.PROGRAM_NAME, true);

		assertThat("error should be about missing missing program name", details,
				hasValidationErrorsIgnoringPath(
						ClinicalDocumentValidator.CONTAINS_PROGRAM_NAME,
						ClinicalDocumentValidator.INCORRECT_PROGRAM_NAME));
	}

	private List<Detail> executeScenario(String templateId, String attribute, boolean remove) {
		String xPath = getPath(templateId, attribute);
		InputStream inStream = upsetTheNorm(xPath, remove);
		Converter converter = new Converter(inStream);
		List<Detail> details = new ArrayList<>();
		try {
			converter.transform();
		} catch (TransformException exception) {
			AllErrors errors = exception.getDetails();
			details.addAll(errors.getErrors().get(0).getDetails());
		}
		return details;
	}

	private String getPath(String templateId, String attribute) {
		String path = PathCorrelator.getXpath(templateId, attribute, NAMESPACE_URI);
		if (path == null) {
			System.out.println("Bad combo templateId: " + templateId + " attribute: " + attribute);
		}
		return "//" + path;
	}

	private InputStream upsetTheNorm(String xPath, boolean remove) {
		try {
			Document document = dbf.newDocumentBuilder().parse(
					new File("../qrda-files/valid-QRDA-III-latest.xml"));
			XPath xpath = xpf.newXPath();
			XPathExpression expression = xpath.compile(xPath);

			NodeList searchedNodes = (NodeList) expression
					.evaluate(document, XPathConstants.NODESET);
			if (searchedNodes == null) {
				System.out.println("bad path: " + xPath);
			} else {
				for (int i = 0; i < searchedNodes.getLength(); i++) {
					Node searched = searchedNodes.item(i);

					Node owningElement = (searched instanceof DeferredElementNSImpl)
							? searched
							: ((DeferredAttrNSImpl) searched).getOwnerElement();

					Node containingParent = owningElement.getParentNode();

					if (remove) {
						containingParent.removeChild(owningElement);
					} else {
						containingParent.appendChild(owningElement.cloneNode(true));
					}

				}
			}

			Transformer t = tf.newTransformer();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			Result result = new StreamResult(os);
			t.transform(new DOMSource(document), result);
			return new ByteArrayInputStream(os.toByteArray());
		} catch (ParserConfigurationException | XPathExpressionException | TransformerException |
				IOException | SAXException ex) {
			throw new RuntimeException(ex);
		}
	}
}
