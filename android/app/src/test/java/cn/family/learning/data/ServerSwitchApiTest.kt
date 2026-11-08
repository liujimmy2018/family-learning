package cn.family.learning.data

import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ServerSwitchApiTest {
    private class Session(initial: String): ApiSession {
        @Volatile override var credentials: JSONObject?=null
        @Volatile override var generation=0L
        @Volatile override var epoch=0L
        override var baseUrl=initial
            @Synchronized set(value){if(field!=value){field=value;credentials=null;generation++;epoch++}}
        @Synchronized fun login(token: String){credentials=JSONObject().put("accessToken",token).put("refreshToken","refresh-$token");generation++}
        @Synchronized override fun saveIfCurrent(expected: Long,value: JSONObject): Boolean {if(expected!=generation)return false;credentials=value;generation++;return true}
        @Synchronized override fun clearIfCurrent(expected: Long){if(expected==generation){credentials=null;generation++;epoch++}}
    }
    private fun response(code: Int=200)=MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody("""{"code":"OK","data":{"ok":true}}""")

    @Test fun lateUnauthorizedResponseCannotRefreshAgainstNewServerOrClearItsCredentials()=runBlocking {
        staleResponse(401)
    }
    @Test fun lateSuccessfulResponseCannotBeDisplayedAsDataFromNewServer()=runBlocking {
        staleResponse(200)
    }
    private suspend fun staleResponse(status: Int)=coroutineScope {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        MockWebServer().use{a->MockWebServer().use{b->
            a.dispatcher=object: Dispatcher(){override fun dispatch(request: RecordedRequest): MockResponse {entered.countDown();check(release.await(5,TimeUnit.SECONDS));return response(status)}}
            a.start();b.start()
            val session=Session(a.url("/").toString().trimEnd('/'));session.login("old-token")
            val api=ApiClient(session)
            val pending=async(Dispatchers.IO){runCatching{api.json("/children")}}
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            api.setUrl(b.url("/").toString());session.login("new-token")
            release.countDown()
            val error=pending.await().exceptionOrNull() as ApiFailure
            assertEquals("SERVER_CHANGED",error.code)
            assertEquals("new-token",session.credentials!!.getString("accessToken"))
            assertEquals(0,b.requestCount)
            b.enqueue(response());api.json("/children")
            val request=b.takeRequest(5,TimeUnit.SECONDS)!!
            assertEquals("Bearer new-token",request.getHeader("Authorization"))
            assertEquals("/api/v1/children",request.path)
            assertEquals("Bearer old-token",a.takeRequest(5,TimeUnit.SECONDS)!!.getHeader("Authorization"))
        }}
    }

    @Test fun normalTokenRefreshStillWorksOnCurrentServer()=runBlocking {
        MockWebServer().use{server->
            server.start();server.enqueue(response(401))
            server.enqueue(MockResponse().setBody("""{"data":{"accessToken":"refreshed","refreshToken":"next"}}"""))
            server.enqueue(response())
            val session=Session(server.url("/").toString().trimEnd('/'));session.login("initial")
            ApiClient(session).json("/children")
            assertEquals("refreshed",session.credentials!!.getString("accessToken"))
            server.takeRequest();val refresh=server.takeRequest();val retry=server.takeRequest()
            assertEquals("/api/v1/auth/refresh",refresh.path)
            assertFalse(refresh.headers.names().contains("Authorization"))
            assertEquals("Bearer refreshed",retry.getHeader("Authorization"))
        }
    }
}
