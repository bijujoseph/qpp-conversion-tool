package gov.cms.qpp.conversion.segmentation;

import gov.cms.qpp.conversion.model.TemplateId;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum QrdaScope {

	PI_AGGREGATE_COUNT(TemplateId.PI_AGGREGATE_COUNT),
	PI_NUMERATOR(TemplateId.PI_NUMERATOR, PI_AGGREGATE_COUNT),
	PI_DENOMINATOR(TemplateId.PI_DENOMINATOR, PI_AGGREGATE_COUNT),
	//PI_NUMERATOR_DENOMINATOR cannot be validated without it's parent
	PI_NUMERATOR_DENOMINATOR(TemplateId.PI_SECTION, TemplateId.REPORTING_PARAMETERS_ACT,
			TemplateId.PI_NUMERATOR_DENOMINATOR, PI_NUMERATOR, PI_DENOMINATOR),
	PI_SECTION(TemplateId.PI_SECTION, TemplateId.REPORTING_PARAMETERS_ACT, PI_NUMERATOR_DENOMINATOR),
	MEASURE_PERFORMED(TemplateId.MEASURE_PERFORMED),
	IA_MEASURE(TemplateId.IA_MEASURE, MEASURE_PERFORMED),
	IA_SECTION(TemplateId.IA_SECTION, TemplateId.REPORTING_PARAMETERS_ACT, IA_MEASURE),

	DEFAULTS(TemplateId.ETHNICITY_SUPPLEMENTAL_DATA_ELEMENT_CMS_V2, TemplateId.SEX_SUPPLEMENTAL_DATA_ELEMENT_CMS_V2,
			TemplateId.RACE_SUPPLEMENTAL_DATA_ELEMENT_CMS_V2, TemplateId.PAYER_SUPPLEMENTAL_DATA_ELEMENT_CMS_V2),

	MEASURE_DATA_CMS_V2(TemplateId.MEASURE_DATA_CMS_V2, TemplateId.REPORTING_STRATUM_CMS, DEFAULTS, PI_AGGREGATE_COUNT),
	MEASURE_REFERENCE_RESULTS_CMS_V2(
			TemplateId.MEASURE_REFERENCE_RESULTS_CMS_V2,
			TemplateId.PERFORMANCE_RATE_PROPORTION_MEASURE, MEASURE_DATA_CMS_V2),
	MEASURE_SECTION_V2(TemplateId.MEASURE_SECTION_V2, MEASURE_REFERENCE_RESULTS_CMS_V2,
					   TemplateId.REPORTING_PARAMETERS_ACT),

	CLINICAL_DOCUMENT(TemplateId.CLINICAL_DOCUMENT, MEASURE_SECTION_V2, IA_SECTION, PI_SECTION);

	private Set<TemplateId> value;

	QrdaScope(Object... templates) {
		value = assemble(templates);
	}

	private Set<TemplateId> assemble(Object... tiers) {
		return Arrays.stream(tiers).flatMap(tier ->
			tier instanceof TemplateId ? Stream.of((TemplateId) tier) : ((QrdaScope) tier).getValue().stream())
			.collect(Collectors.toSet());
	}

	public static QrdaScope getInstanceByName(String name) {
		Objects.requireNonNull(name, "name");
		String match = name.trim().replace(' ', '_');
		return Arrays.stream(QrdaScope.values())
				.filter(value -> value.name().equalsIgnoreCase(match))
				.findFirst()
				.orElse(null);
	}

	public static Set<TemplateId> getTemplates(Collection<QrdaScope> scopes) {
		if (scopes == null) {
			return Collections.emptySet();
		}

		return scopes.stream()
				.flatMap(scope -> scope.getValue().stream())
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(TemplateId.class)));
	}

	public static Set<String> getNames() {
		return Arrays.stream(values()).map(QrdaScope::name).collect(Collectors.toSet());
	}

	public Set<TemplateId> getValue() {
		return value;
	}
}
