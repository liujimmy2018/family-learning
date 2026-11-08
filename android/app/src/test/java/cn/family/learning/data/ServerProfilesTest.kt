package cn.family.learning.data

import org.junit.Assert.*
import org.junit.Test

class ServerProfilesTest {
    @Test fun normalizesAddressesAndKeepsNondefaultPorts() {
        assertEquals("http://192.168.0.177:8080",ServerAddresses.normalize(" http://192.168.0.177:8080/ ",true))
        assertEquals("https://example.com",ServerAddresses.normalize("HTTPS://EXAMPLE.COM:443/",false))
        assertEquals("http://example.com",ServerAddresses.normalize("http://example.com:80",true))
        assertEquals("http://[::1]:8080",ServerAddresses.normalize("http://[::1]:8080/",true))
    }
    @Test fun rejectsWrongSchemesCredentialsPathsParametersAndPorts() {
        listOf("192.168.1.10:8080","ftp://example.com","http://u:p@example.com","http://example.com/api/v1",
            "http://example.com/?q=x","http://example.com/#x","http://example.com:0","http://example.com:65536",
            "http://","http://a b","http://example.com//").forEach {raw->
            assertTrue(raw,runCatching{ServerAddresses.normalize(raw,true)}.isFailure)
        }
        assertTrue(runCatching{ServerAddresses.normalize("http://example.com",false)}.isFailure)
    }
    @Test fun createsAndEditsMultipleServersWithoutChangingOtherEntries() {
        val first=ServerAddresses.save(emptyList(),null," 家里 ","http://home:8080")
        val both=ServerAddresses.save(first,null,"办公室","http://office:8080")
        assertEquals(2,both.size)
        assertEquals("家里",both.first().name)
        val edited=ServerAddresses.save(both,first.single().id,"家里电脑","http://home:8081")
        assertEquals(first.single().id,edited.first().id)
        assertEquals(both.last(),edited.last())
    }
    @Test fun rejectsDuplicateAddressesEmptyNamesAndEditingMissingProfiles() {
        val list=ServerAddresses.save(emptyList(),null,"家里","http://home:8080")
        assertTrue(runCatching{ServerAddresses.save(list,null,"另一个名字","http://home:8080")}.isFailure)
        assertTrue(runCatching{ServerAddresses.save(list,null," ","http://office:8080")}.isFailure)
        assertTrue(runCatching{ServerAddresses.save(list,null,"a".repeat(41),"http://office:8080")}.isFailure)
        assertTrue(runCatching{ServerAddresses.save(list,"missing","办公室","http://office:8080")}.isFailure)
    }
    @Test fun separatesIdenticalAccountIdsAcrossServersAndNormalizesEquivalentEndpoints() {
        assertNotEquals(ServerAddresses.owner("http://home:8080","same-id"),ServerAddresses.owner("http://office:8080","same-id"))
        assertNotEquals(ServerAddresses.owner("http://home:8080","user-a"),ServerAddresses.owner("http://home:8080","user-b"))
        assertEquals(ServerAddresses.owner("HTTPS://EXAMPLE.COM:443/","user"),ServerAddresses.owner("https://example.com","user"))
    }
}
