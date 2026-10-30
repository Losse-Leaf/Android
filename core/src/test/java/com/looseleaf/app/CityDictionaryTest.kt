package com.looseleaf.app

import com.looseleaf.app.utils.CityDictionary
import org.junit.Assert.assertEquals
import org.junit.Test

class CityDictionaryTest {

    @Test
    fun testLongestMatchCityCode() {
        assertEquals("310100", CityDictionary.codeForLocation("上海市徐汇区武康路"))
        assertEquals("532900", CityDictionary.codeForLocation("云南省大理白族自治州古城"))
        assertEquals("511500", CityDictionary.codeForLocation("四川省宜宾市翠屏区"))
        assertEquals("330100", CityDictionary.codeForLocation("杭州市西湖区龙井村"))
    }
}
