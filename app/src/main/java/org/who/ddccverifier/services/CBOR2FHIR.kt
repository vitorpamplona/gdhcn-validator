package org.who.ddccverifier.services

import android.content.Context
import android.util.Log
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineBuilder
import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import org.hl7.fhir.instance.model.api.IBaseDatatype
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.utils.CodingUtilities
import org.who.ddccverifier.views.ResultFragment
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import org.hl7.fhir.r4.model.IdType

class CBOR2FHIR {
    // Using old java.time to keep compatibility down to Android SDK 22.
    private var dfISO: DateFormat = SimpleDateFormat("yyyy-MM-dd")
    private fun parseDate(date: CBORObject?): Date? {
        if (date == null || date.isUndefined) return null
        return dfISO.parse(date.AsString())!!
    }

    private fun parseDateTimeType(date: CBORObject?): DateTimeType? {
        if (date == null || date.isUndefined) return null
        return DateTimeType().apply {
            value = parseDate(date)
        }
    }

    private fun parsePositiveIntType(positiveInt: CBORObject?): PositiveIntType? {
        if (positiveInt == null || positiveInt.isUndefined) return null
        return PositiveIntType().apply {
            value = positiveInt.AsInt32Value()
        }
    }

    private fun parseIdentifier(obj: CBORObject?): Identifier? {
        if (obj == null || obj.isUndefined) return null
        return Identifier().apply {
            value = obj.AsString()
        }
    }

    private fun parseIdentifierReference(obj: CBORObject?): Reference? {
        if (obj == null || obj.isUndefined) return null
        return Reference().apply {
            if (obj.type == CBORType.Map) {
                identifier = Identifier().apply {
                    value = obj["code"]?.AsString()
                    system = obj["system"]?.AsString()
                }
            } else {
                this.setId(obj?.AsString())
            }
        }
    }

    private fun parsePerformer(obj: CBORObject?) : Immunization.ImmunizationPerformerComponent? {
        if (obj == null || obj.isUndefined) return null
        return Immunization.ImmunizationPerformerComponent().apply {
            actor = parseReference(obj)
            actor?.setType("Practitioner")
        }
    }

    private fun parseLocation(obj: CBORObject?): Reference? {
        if (obj == null || obj.isUndefined) return null
        return Reference().apply {
            display = obj.AsString()
        }
    }

    private fun parseCoding(obj: CBORObject?): Coding? {
        if (obj == null || obj.isUndefined) return null
        return Coding().apply {
            code = obj["code"]?.AsString()
            system = obj["system"]?.AsString()
        }
    }

    private fun parseCodableConcept(obj: CBORObject?): CodeableConcept? {
        if (obj == null || obj.isUndefined) return null
        return CodeableConcept(parseCoding(obj))
    }

    private fun parseReference(obj: CBORObject?): Reference? {
        if (obj == null || obj.isUndefined) return null
        return Reference().apply {
            identifier = Identifier().apply {
                value = obj.AsString()
            }
        }
    }

    private fun parseExtension(value: IBaseDatatype?, url: String): Extension? {
        if (value == null) return null
        return Extension().apply {
            setUrl(url)
            setValue(value)
        }
    }

    private fun parseGender(obj: CBORObject?): Enumerations.AdministrativeGender? {
        if (obj == null || obj.isUndefined) return null
        return Enumerations.AdministrativeGender.fromCode(obj["code"].AsString())
    }

    private fun parseHumanName(obj: CBORObject?): HumanName? {
        if (obj == null || obj.isUndefined) return null
        return HumanName().apply {
            val names = obj.AsString().split(" ")
            val givenNames = names.subList(0,names.size-1)
            givenNames.forEach {
                    name -> addGiven(name)
            }
            family = names.last()
        }
    }

    private fun createRecommendationBasedOn(due_date: CBORObject?, immunization: Immunization): ImmunizationRecommendation? {
        if (due_date == null || due_date.isUndefined) return null
        return ImmunizationRecommendation().apply {
            patient = immunization.patient
            recommendation = listOf(ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent().apply {
                vaccineCode = listOfNotNull(immunization.vaccineCode)
                targetDisease = immunization.protocolAppliedFirstRep.targetDiseaseFirstRep
                doseNumber = PositiveIntType().setValue(immunization.protocolAppliedFirstRep.doseNumberPositiveIntType.value +1)
                seriesDoses = immunization.protocolAppliedFirstRep.seriesDoses
                forecastStatus = CodeableConcept(Coding("http://terminology.hl7.org/2.1.0/CodeSystem-immunization-recommendation-status.html", "due", ""))
                dateCriterion = listOf(ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent().apply {
                    code = CodeableConcept(Coding("http://loinc.org", "30980-7", "Date vaccine due"))
                    value = parseDate(due_date)
                })
            })
        }
    }

    fun parsePatient(DDCC: CBORObject): Patient {
        return Patient().apply{
            name = listOf(parseHumanName(DDCC["name"]))
            identifier = listOfNotNull(parseIdentifier(DDCC["identifier"]))
            birthDate = parseDate(DDCC["birthDate"])
            gender = parseGender(DDCC["sex"])
        }
    }

    fun run(DDCC: CBORObject): Bundle {
        val _patient = parsePatient(DDCC);

        val _immunization = Immunization().apply{
            patient = Reference().apply {
                this.identifier = parseIdentifier(DDCC["identifier"])
            }
            vaccineCode = parseCodableConcept(DDCC["vaccine"])
            occurrence = parseDateTimeType(DDCC["date"]);
            lotNumber = DDCC["lot"]?.AsString()
            protocolApplied = listOfNotNull(Immunization.ImmunizationProtocolAppliedComponent().apply {
                targetDisease = listOfNotNull(parseCodableConcept(DDCC["disease"]))
                doseNumber = parsePositiveIntType(DDCC["dose"])
                seriesDoses = parsePositiveIntType(DDCC["total_doses"])
                authority = parseReference(DDCC["pha"])
                authority?.setType("Organization")
            })
            location = parseLocation(DDCC["centre"])
            performer = listOfNotNull(parsePerformer(DDCC["hw"]))
            manufacturer = parseIdentifierReference(DDCC["manufacturer"])
            extension = listOfNotNull(
                parseExtension(parseCoding(DDCC["brand"]), "https://WorldHealthOrganization.github.io/ddcc/StructureDefinition/DDCCVaccineBrand"),
                parseExtension(parseCoding(DDCC["ma_holder"]), "https://WorldHealthOrganization.github.io/ddcc/StructureDefinition/DDCCVaccineMarketAuthorization"),
                parseExtension(parseCoding(DDCC["country"]), "https://WorldHealthOrganization.github.io/ddcc/StructureDefinition/DDCCCountryOfVaccination"),
                parseExtension(parseDateTimeType(DDCC["vaccine_valid"]), "https://WorldHealthOrganization.github.io/ddcc/StructureDefinition/DDCCVaccineValidFrom"),
            )
        }

        val _recommendation = createRecommendationBasedOn(DDCC["due_date"], _immunization)

        val _composition = Composition().apply {
            type = CodeableConcept(Coding("http://loinc.org", "82593-5", "Immunization summary report"))
            category = listOf(CodeableConcept(Coding().apply {
                code = "ddcc-vs"
            }))
            subject = Reference().apply {
                this.identifier = parseIdentifier(DDCC["identifier"])
            }
            title = "International Certificate of Vaccination or Prophylaxis"
            event = listOf(Composition.CompositionEventComponent().apply {
                period = Period().apply {
                    start = parseDate(DDCC["valid_from"])
                    end = parseDate(DDCC["valid_until"])
                }
            })
            author = listOfNotNull(_immunization.protocolAppliedFirstRep.authority)
            section = listOf(Composition.SectionComponent().apply {
                code = CodeableConcept(Coding("http://loinc.org", "11369-6", "History of Immunization Narrative"))
                author = listOfNotNull(_immunization.protocolAppliedFirstRep.authority)
                focus = Reference(_immunization.id)
                entry = listOfNotNull(
                    Reference(_immunization.id),
                    _recommendation?.let { Reference(_recommendation?.id) }
                )
            })
        }

        return Bundle().apply {
            type = Bundle.BundleType.TRANSACTION
            addEntry().setResource(_patient)
            addEntry().setResource(_immunization)
            if (_recommendation != null) addEntry().setResource(_recommendation)
            addEntry().setResource(_composition)
        }
    }
}