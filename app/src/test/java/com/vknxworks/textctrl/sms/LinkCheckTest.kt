package com.vknxworks.textctrl.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkCheckTest {
    @Test fun ordinaryLink() {
        val i = LinkCheck.inspect("https://www.Example.com/pay?x=1#top")
        assertEquals("www.example.com", i.host)
        assertFalse(i.lookalike || i.numeric || i.disguised || i.unencrypted)
    }

    @Test fun nameInFrontOfAtIsNotTheSite() {
        val i = LinkCheck.inspect("https://bpost.be@evil.example:8443/track")
        assertEquals("evil.example", i.host)
        assertTrue(i.disguised)
    }

    @Test fun numbersLookalikesAndPlainHttp() {
        assertTrue(LinkCheck.inspect("http://192.168.1.10/login").numeric)
        assertTrue(LinkCheck.inspect("http://192.168.1.10/login").unencrypted)
        assertTrue(LinkCheck.inspect("https://[2001:db8::1]:443/").numeric)
        assertTrue(LinkCheck.inspect("https://xn--pypal-4ve.com/").lookalike)
        assertTrue(LinkCheck.inspect("https://pаypal.com/").lookalike)
        assertFalse(LinkCheck.inspect("https://1.2.3.example/").numeric)
    }

    @Test fun atSignInThePathDoesNotCount() {
        val i = LinkCheck.inspect("https://example.com/@user")
        assertEquals("example.com", i.host)
        assertFalse(i.disguised)
    }
}
