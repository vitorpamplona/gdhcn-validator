package org.who.gdhcnvalidator.trust.didweb

import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import org.who.gdhcnvalidator.trust.TrustRegistry
import org.who.gdhcnvalidator.trust.didweb.GDHCNTrustRegistry.Companion.ACCEPTANCE_REGISTRY
import java.net.URI

class DDCCTrustRegistryTest {
    companion object {
        const val PROD_KEY_ID = "did:web:raw.githubusercontent.com:WorldHealthOrganization:ddcc-trust:main:dist:prod:u:k"
        const val TEST_KEY_ID = "did:web:raw.githubusercontent.com:WorldHealthOrganization:ddcc-trust:main:dist:test:u:k"

        const val PROD_DID = "did:web:raw.githubusercontent.com:WorldHealthOrganization:ddcc-trust:main:dist:prod:u:ml:e"  //this is UAT not PROD!!!
        const val TEST_DID = "did:web:raw.githubusercontent.com:WorldHealthOrganization:ddcc-trust:main:dist:test:u:ml:e"

        val PRODUCTION_REGISTRY = TrustRegistry.RegistryEntity("DDCC-Trust Prod", TrustRegistry.Scope.PRODUCTION, URI(PROD_DID), PROD_KEY_ID, null)
        val ACCEPTANCE_REGISTRY =  TrustRegistry.RegistryEntity("DDCC-Trust Acceptance", TrustRegistry.Scope.ACCEPTANCE_TEST, URI(TEST_DID), TEST_KEY_ID, null)

        var registry = GDHCNTrustRegistry()
        @BeforeClass @JvmStatic fun setup() {
            registry.init(PRODUCTION_REGISTRY, ACCEPTANCE_REGISTRY)
        }
    }

    @Test
    fun loadEntity() {
        val t = registry.resolve(TrustRegistry.Framework.DIVOC, "india")
        assertNotNull(t)
    }

    @Test
    fun testSHCWA() {
        val t = registry.resolve(TrustRegistry.Framework.SHC, "https://waverify.doh.wa.gov/creds#n0S0H6_mbA93e3pEu-a67qoiF4CAWYsOGoWo6TLHUzQ")
        assertNotNull(t)
    }

    @Test
    fun testDCCItalyAcceptance() {
        val t = registry.resolve(TrustRegistry.Framework.DCC, "OTAXaM3aBRM=")
        assertNotNull(t)
    }

    @Test
    fun testSCHSenegal() {
        val t = registry.resolve(TrustRegistry.Framework.SHC, "https://senegal.tbi.ohms.oracle.com#VEccqX9LvPZJXqv11staEs0qPN2OR9bMS_PXEAZODXg")
        assertNotNull(t)
    }

    @Test
    fun testICAOAustrala() {
        val t = registry.resolve(TrustRegistry.Framework.ICAO, "AU#NhfB5/VnlXEuN3VwjlWDMYbpOA4=")
        assertNotNull(t)
    }

    @Test
    fun testICAOJapan() {
        val t = registry.resolve(TrustRegistry.Framework.ICAO, "JP#arTykoK9lkf2/yoC95RNdJ6XhGM=")
        assertNotNull(t)
    }
}

