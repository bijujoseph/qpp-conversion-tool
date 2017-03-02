package gov.cms.qpp.conversion.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jdom2.Element;
import org.junit.Before;
import org.junit.Test;

import gov.cms.qpp.conversion.decode.AciNumeratorDenominatorDecoder;
import gov.cms.qpp.conversion.decode.DecodeException;
import gov.cms.qpp.conversion.decode.InputDecoder;
import gov.cms.qpp.conversion.encode.AciNumeratorDenominatorValueEncoder;

public class RegistryTest {

	Registry<String, InputDecoder> registry;

	@Before
	public void before() {
		registry = new Registry<>(XmlDecoder.class);
	}

	@Test
	public void testRegistryExistsByDefault() throws Exception {
		try {
			registry.register("id", Placeholder.class);
		} catch (NullPointerException e) {
			fail("Registry should always exist.");
		}
		assertTrue("Registry exists", true);
	}

	@Test
	public void testRegistryInit() throws Exception {
		registry.register("id", Placeholder.class);
		registry.init();
		InputDecoder decoder = (InputDecoder) registry.get("id");
		assertTrue("Registry should have been reset.", decoder == null);
	}

	@Test
	public void testRegistryGetConverterHandler() throws Exception {
		registry.register("id", Placeholder.class);
		InputDecoder decoder = (InputDecoder) registry.get("id");
		assertTrue("Registry should have been reset.", decoder instanceof Placeholder);
	}

	// This test must reside here in order to call the protected methods on the
	// registry
	@Test
	public void testRegistry_placeAndFetch() throws Exception {
		String templateId = registry.getAnnotationParam(AciNumeratorDenominatorDecoder.class);
		InputDecoder decoder = (InputDecoder) registry.get(templateId);

		assertNotNull("A handler is expected", decoder);
		assertEquals("Handler should be an instance of the handler for the given XPATH",
				AciNumeratorDenominatorDecoder.class, decoder.getClass());
	}

	@Test
	public void testRegistry_getAnnotationParam() throws Exception {
		String templateId = registry.getAnnotationParam(AciNumeratorDenominatorDecoder.class);
		assertNotNull("A templateId is expected", templateId);
		assertEquals("The templateId should be", "2.16.840.1.113883.10.20.27.3.3", templateId);

		templateId = new Registry<String, Encoder>(Encoder.class).getAnnotationParam(AciNumeratorDenominatorValueEncoder.class);
		assertNotNull("A templateId is expected", templateId);
		assertEquals("The templateId should be", "2.16.840.1.113883.10.20.27.3.3", templateId);
	}

	@Test
	public void testRegistry_getAnnotationParam_NullReturn() throws Exception {
		String templateId = new Registry<String, Encoder>(SuppressWarnings.class).getAnnotationParam(Placeholder.class);
		assertTrue("A templateId is expected", templateId == null);
	}

	@Test
	public void testRegistryGetHandlerThatFailsConstruction() throws Exception {
		registry.register("id", PrivateConstructor.class);
		InputDecoder decoder = (InputDecoder) registry.get("id");
		assertThat("Registry should return null for faile construction not an exception.", decoder, is(nullValue()));
	}

	@Test
	public void testClassNotFoundCausesMissingEntriesInRegistry_throwsNoException() {
		Registry<String,XmlDecoder> registryA = new Registry<String,XmlDecoder>(XmlDecoder.class);
		
		// Mock the condition where a class is not found during registry building
		Registry<String,XmlDecoder> registryB = new Registry<String,XmlDecoder>(XmlDecoder.class) {
			@Override
			protected Class<?> getAnnotatedClass(String className) throws ClassNotFoundException {
				if ("gov.cms.qpp.conversion.decode.AciNumeratorDenominatorDecoder".equals(className)) {
					throw new ClassNotFoundException();
				}
				return Class.forName(className);
			}
		};
		
		assertEquals("The class not found should cause B to be missing a decoder",
				registryA.registry.size(), registryB.registry.size() + 1);
	}
}

@SuppressWarnings("unused") // this is here for a the annotation tests
class Placeholder implements InputDecoder {
	private String unused;

	public Placeholder() {
	}

	@Override
	public Node decode(Element xmlDoc) throws DecodeException {
		return null;
	}
};

class PrivateConstructor implements InputDecoder {
	private PrivateConstructor() {
	}

	@Override
	public Node decode(Element xmlDoc) throws DecodeException {
		return null;
	}
};