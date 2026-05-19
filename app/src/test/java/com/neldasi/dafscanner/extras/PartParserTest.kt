package com.neldasi.dafscanner.extras

import org.junit.Assert.*
import org.junit.Test

class PartParserTest {

    @Test
    fun testMX11() {
        val code = "215000188429939DE000000000K6805"
        val parsed = parseScannedCode(code)
        
        assertNotNull(parsed)
        assertEquals(EngineFormat.MX11, parsed?.format)
        assertEquals("2150001", parsed?.typeCode)
        assertEquals("88429", parsed?.supplierCode)
        assertEquals("939DE0", parsed?.serialHex)
        assertEquals("9674208", parsed?.serialDecimal)
    }

    @Test
    fun testMX13_v1() {
        val code = "226132588280030A3500740199221"
        val parsed = parseScannedCode(code)
        
        assertNotNull(parsed)
        assertEquals(EngineFormat.MX13, parsed?.format)
        assertEquals("2261325", parsed?.typeCode)
        assertEquals("88280", parsed?.supplierCode)
        assertEquals("030A35", parsed?.serialHex)
        assertEquals("199221", parsed?.serialDecimal)
    }

    @Test
    fun testMX13_v2() {
        val code = "22452958828001741300740095251"
        val parsed = parseScannedCode(code)
        
        assertNotNull(parsed)
        assertEquals(EngineFormat.MX13, parsed?.format)
        assertEquals("2245295", parsed?.typeCode)
        assertEquals("88280", parsed?.supplierCode)
        assertEquals("017413", parsed?.serialHex)
        assertEquals("95251", parsed?.serialDecimal)
    }

    @Test
    fun testP14_v1() {
        val code = "247307388280825692780010027"
        val parsed = parseScannedCode(code)
        
        assertNotNull(parsed)
        assertEquals(EngineFormat.P14, parsed?.format)
        assertEquals("2473073", parsed?.typeCode)
        assertEquals("88280", parsed?.supplierCode)
        assertEquals("00272B", parsed?.serialHex)
        assertEquals("0010027", parsed?.serialDecimal)
    }

    @Test
    fun testP14_v2() {
        val code = "247307188280825693780010117"
        val parsed = parseScannedCode(code)
        
        assertNotNull(parsed)
        assertEquals(EngineFormat.P14, parsed?.format)
        assertEquals("2473071", parsed?.typeCode)
        assertEquals("002785", parsed?.serialHex)
        assertEquals("0010117", parsed?.serialDecimal)
    }

    @Test
    fun testP14_with_74_marker() {
        // Example provided by user: 259964588280828335740000088
        val code = "259964588280828335740000088"
        val parsed = parseScannedCode(code)
        
        assertNotNull(parsed)
        assertEquals(EngineFormat.P14, parsed?.format)
        assertEquals("2599645", parsed?.typeCode)
        assertEquals("000058", parsed?.serialHex) // 88 dec = 58 hex, padded to 6 chars
        assertEquals("0000088", parsed?.serialDecimal)
    }
}
