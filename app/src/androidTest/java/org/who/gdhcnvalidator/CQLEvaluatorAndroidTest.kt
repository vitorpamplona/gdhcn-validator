package org.who.gdhcnvalidator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.knowledge.KnowledgeManager
import com.google.android.fhir.testing.jsonParser
import com.google.android.fhir.workflow.FhirOperator
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.*
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.who.gdhcnvalidator.services.cql.CqlBuilder
import org.who.gdhcnvalidator.test.BaseTrustRegistryTest
import org.who.gdhcnvalidator.verify.divoc.DivocVerifier
import org.who.gdhcnvalidator.verify.hcert.HCertVerifier
import org.who.gdhcnvalidator.verify.icao.IcaoVerifier
import org.who.gdhcnvalidator.verify.shc.ShcVerifier
import java.io.File
import java.util.*


class CQLEvaluatorAndroidTest: BaseTrustRegistryTest() {
    @get:Rule
    val fhirEngineProviderRule = FhirEngineProviderTestRule()

    private val fhirContext = FhirContext.forCached(FhirVersionEnum.R4)
    private val jSONParser = fhirContext.newJsonParser()

    private lateinit var fhirEngine: FhirEngine
    private lateinit var fhirOperator: FhirOperator
    private lateinit var knowledgeManager: KnowledgeManager

    private val ddccPass = CqlBuilder.compileAndBuild(inputStream("TestPass-1.0.0.cql")!!)

    private fun writeToFile(resource: BaseResource): File {
        val context: Context = ApplicationProvider.getApplicationContext()
        return File(context.cacheDir, resource.id).apply {
            val json = jsonParser.encodeResourceToString(resource)
            writeText(json)
        }
    }

    @Before
    fun setUp() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        fhirEngine = FhirEngineProvider.getInstance(ctx)
        knowledgeManager = KnowledgeManager.create(
            context = ctx,
            inMemory = true,
            downloadedNpmDir = ctx.cacheDir
        )
        fhirOperator = FhirOperator.Builder(ApplicationProvider.getApplicationContext())
            .fhirEngine(fhirEngine)
            .fhirContext(fhirContext)
            .knowledgeManager(knowledgeManager)
            .build()

        knowledgeManager.index(writeToFile(ddccPass))

        TimeZone.setDefault(TimeZone.getTimeZone("GMT"))
    }

    private suspend fun loadBundle(bundle: Bundle?) {
        checkNotNull(bundle)
        for (entry in bundle.entry) {
            when (entry.resource.resourceType) {
                ResourceType.Library -> knowledgeManager.index(writeToFile(entry.resource as Library))
                ResourceType.Bundle -> Unit
                else -> fhirEngine.create(entry.resource)
            }
        }
    }

    private fun patId(bundle: Bundle?): String {
        checkNotNull(bundle)
        return bundle.entry.filter { it.resource is Patient }.first().resource.id.removePrefix("Patient/")
    }

    @Test
    fun evaluateTestPassOnWHOQR1FromCompositonTest() = runBlocking {
        val asset = jSONParser.parseResource(open("WHOQR1FHIRBundle.json")) as Bundle

        loadBundle(asset)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(asset),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(false, results.getParameterBool("CompletedImmunization"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetSingleDose"))
    }


    @Test
    fun evaluateTestPassOnWHOQR2FromCompositonTest() = runBlocking {
        val asset = jSONParser.parseResource(open("WHOQR2FHIRBundle.json")) as Bundle

        loadBundle(asset)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(asset),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(true, results.getParameterBool("CompletedImmunization"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
        Assert.assertNotEquals(Collections.EMPTY_LIST, results.getParameters("GetSingleDose"))
    }

    @Test
    fun evaluateTestPassOnDVCFromQRTest() = runBlocking {
        val qr1 = open("DVCTestQR.txt")
        val verified = HCertVerifier(registry).unpackAndVerify(qr1)

        loadBundle(verified.contents)

        Assert.assertEquals(QRDecoder.Status.ISSUER_NOT_TRUSTED, verified.status)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(verified.contents),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(true, results.getParameterBool("CompletedImmunization"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
    }

    @Ignore("QR codes created in the first version are now invalid")
    @Test
    fun evaluateTestPassOnWHOQR2FromQRTest() = runBlocking {
        val qr1 = open("WHOQR2Contents.txt")
        val verified = HCertVerifier(registry).unpackAndVerify(qr1)

        Assert.assertEquals(QRDecoder.Status.VERIFIED, verified.status)

        loadBundle(verified.contents)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(verified.contents),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(true, results.getParameterBool("CompletedImmunization"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
        Assert.assertNotEquals(Collections.EMPTY_LIST, results.getParameters("GetSingleDose"))
    }

    @Test
    fun evaluateTestPassOnEUQR1FromQRTest() = runBlocking {
        val qr1 = open("EUQR1Contents.txt")
        val verified = HCertVerifier(registry).unpackAndVerify(qr1)

        loadBundle(verified.contents)

        Assert.assertEquals(QRDecoder.Status.VERIFIED, verified.status)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(verified.contents),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(true, results.getParameterBool("CompletedImmunization"))
        Assert.assertNotEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetSingleDose"))
    }

    @Test
    fun evaluateTestPassOnSHCQR1FromQRTest() = runBlocking {
        val qr1 = open("SHCQR1Contents.txt")
        val verified = ShcVerifier(registry).unpackAndVerify(qr1)

        loadBundle(verified.contents)

        Assert.assertEquals(QRDecoder.Status.VERIFIED, verified.status)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(verified.contents),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(true, results.getParameterBool("CompletedImmunization"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetSingleDose"))
    }

    @Test
    @Ignore("Keys are not in the trust registry anymore")
    fun evaluateTestPassOnDIVOCQR1FromQRTest() = runBlocking {
        val qr1 = open("DIVOCQR1Contents.txt")
        val verified = DivocVerifier(registry).unpackAndVerify(qr1)

        loadBundle(verified.contents)

        Assert.assertEquals(QRDecoder.Status.VERIFIED, verified.status)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(verified.contents),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(false, results.getParameterBool("CompletedImmunization"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetSingleDose"))
    }

    @Test
    fun evaluateTestPassOnICAOQR1FromQRTest() = runBlocking {
        val qr1 = open("ICAOAUQR1Contents.txt")
        val verified = IcaoVerifier(registry).unpackAndVerify(qr1)

        loadBundle(verified.contents)

        Assert.assertEquals(QRDecoder.Status.VERIFIED, verified.status)

        val results = fhirOperator.evaluateLibrary(
            "http://localhost/Library/TestPass|1.0.0",
            patId(verified.contents),
            setOf("CompletedImmunization", "GetFinalDose", "GetSingleDose")) as Parameters

        Assert.assertEquals(true, results.getParameterBool("CompletedImmunization"))
        Assert.assertEquals(Collections.EMPTY_LIST, results.getParameters("GetFinalDose"))
        Assert.assertNotEquals(Collections.EMPTY_LIST, results.getParameters("GetSingleDose"))
    }
}