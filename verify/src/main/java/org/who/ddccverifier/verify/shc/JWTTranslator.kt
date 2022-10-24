package org.who.ddccverifier.verify.shc

import ca.uhn.fhir.model.api.TemporalPrecisionEnum
import org.hl7.fhir.r4.model.*
import java.util.*

/**
 * Finds the right translator for the JWT content using SHC
 */
class JWTTranslator {

    fun getPatient(payload: JWTPayload): Patient {
        return payload.vc?.credentialSubject?.fhirBundle?.entry
            ?.filter { it -> it.resource.fhirType() == "Patient" }
            ?.first()?.resource as Patient
    }

    fun getImmunizations(payload: JWTPayload): List<Resource> {
        return payload.vc?.credentialSubject?.fhirBundle?.entry
            ?.filter { it -> it.resource.fhirType() == "Immunization" }
            ?.map {it -> it.resource} ?: listOf()
    }

    fun getObservations(payload: JWTPayload): List<Resource> {
        return payload.vc?.credentialSubject?.fhirBundle?.entry
            ?.filter { it -> it.resource.fhirType() == "Observation" }
            ?.map {it -> it.resource} ?: listOf()
    }

    private fun parseDateType(date: Double?): DateTimeType? {
        if (date == null) return null
        return DateTimeType(Date((date*1000).toLong()), TemporalPrecisionEnum.DAY)
    }

    fun toFhir(payload: JWTPayload): Bundle {
        val myPatient = getPatient(payload)
        val myImmunizations = getImmunizations(payload)
        val myObservations = getObservations(payload)

        val organization = Organization().apply {
            identifier = listOf(Identifier().apply {
                value = payload.iss.toString()
            })
        }

        val immunizations = mutableListOf<Reference>()
        for (imm in myImmunizations) {
            immunizations.add(Reference(imm))
        }

        val observations = mutableListOf<Reference>()
        for (obs in myObservations) {
            observations.add(Reference(obs))
        }

        val myComposition = Composition().apply {
            id = payload.jti.toString()
            type = CodeableConcept(Coding("http://loinc.org", "82593-5", "Immunization summary report"))
            category = listOf(CodeableConcept(Coding().apply {
                code = "ddcc-vs"
            }))
            subject = Reference(myPatient)
            title = "International Certificate of Vaccination or Prophylaxis"
            author = listOf(Reference(organization))
            section = listOfNotNull(
                if (immunizations.isEmpty()) null else Composition.SectionComponent().apply {
                    code = CodeableConcept(Coding("http://loinc.org", "11369-6", "History of Immunization Narrative"))
                    author = listOf(Reference(organization))
                    entry = immunizations
                },
                if (observations.isEmpty()) null else Composition.SectionComponent().apply {
                    code = CodeableConcept(Coding("http://loinc.org", "30954-2", "Results (Diagnostic findings)"))
                    author = listOf(Reference(organization))
                    entry = observations
                }
            )
        }

        val b = Bundle()
        b.type = Bundle.BundleType.TRANSACTION
        b.addEntry().resource = myComposition
        b.addEntry().resource = myPatient
        for (imm in myImmunizations) {
            b.addEntry().resource = imm
        }
        for (obs in myObservations) {
            b.addEntry().resource = obs
        }

        return b
    }
}