package cn.family.learning;

import cn.family.learning.common.Values;

import cn.family.learning.ai.DashScopeProvider;
import cn.family.learning.auth.Tokens;
import cn.family.learning.common.ApiException;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import static org.junit.jupiter.api.Assertions.*;

class ProviderContractTest {
    @Test void recognitionRejectsInventedOrMalformedOutput(){
        assertThrows(ApiException.class,()->DashScopeProvider.parseWords("not json"));
        assertThrows(ApiException.class,()->DashScopeProvider.parseWords("{\"words\":[{\"text\":\"apple\"}]}"));
        assertThrows(ApiException.class,()->DashScopeProvider.parseWords("{\"words\":[]}"));
        assertEquals("苹果",DashScopeProvider.parseWords("{\"words\":[{\"text\":\" apple \",\"meaning\":\"苹果\"}]}").get(0).meaning());
    }
    @Test void providerCannotMakeServerFetchPrivateOrArbitraryUrls(){
        for(String value:new String[]{"http://127.0.0.1/a.mp3","https://127.0.0.1/a.mp3","https://evil.com/a.mp3","https://oss.aliyuncs.com.evil.com/a.mp3","https://user@bucket.aliyuncs.com/a.mp3","https://bucket.aliyuncs.com:8443/a.mp3"})
            assertThrows(ApiException.class,()->DashScopeProvider.validateAudioUrl(URI.create(value)),value);
        assertDoesNotThrow(()->DashScopeProvider.validateAudioUrl(URI.create("https://bucket.oss-cn-beijing.aliyuncs.com/tts.wav")));
    }
    @Test void sessionTokensAreRandomAndOnlyHashesAreStored(){
        String a=Tokens.random(),b=Tokens.random();assertNotEquals(a,b);assertEquals(43,a.length());assertEquals(64,Tokens.hash(a).length());assertNotEquals(a,Tokens.hash(a));
    }
    @Test void missingKeyNeverProducesFakeRecognizedWords(){
        DashScopeProvider provider=new DashScopeProvider("","https://dashscope.aliyuncs.com","qwen3-vl-plus","qwen3-tts-flash");
        ApiException e=assertThrows(ApiException.class,()->provider.recognize(Values.list(),"WORD_TABLE",3));assertEquals("AI_NOT_CONFIGURED",e.code);
    }
    @Test void legacyJsonTextEscapesSupplementaryCharactersWithoutLosingValues()throws Exception {
        String value="学习\uD83D\uDE00";
        String json=cn.family.learning.common.Json.write(Values.map("note",value));
        for(char character:json.toCharArray())assertTrue(character<128);
        assertEquals(value,cn.family.learning.common.Json.MAPPER.readTree(json).path("note").asText());
        assertEquals("AI_TEXT_UNSUPPORTED",assertThrows(ApiException.class,()->DashScopeProvider.parseWords("{\"words\":[{\"text\":\"apple\",\"meaning\":\"苹果\\uD83D\\uDE00\"}]}")).code);
    }
    @Test void java8HttpAdapterSendsAuthenticatedUtf8Json()throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().addHeader("Content-Type","application/json")
                    .setBody("{\"choices\":[{\"message\":{\"content\":\"{\\\"words\\\":[{\\\"text\\\":\\\"apple\\\",\\\"meaning\\\":\\\"苹果\\\"}]}\"}}]}"));
            DashScopeProvider provider=new DashScopeProvider("test-secret",server.url("").toString(),"vision-test","speech-test");
            assertEquals("苹果",provider.recognize(Values.list(),"WORD_TABLE",3).get(0).meaning());
            RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);
            assertNotNull(request);assertEquals("/compatible-mode/v1/chat/completions",request.getPath());
            assertEquals("Bearer test-secret",request.getHeader("Authorization"));
            assertTrue(cn.family.learning.common.Json.MAPPER.readTree(request.getBody().readUtf8())
                    .path("messages").path(0).path("content").path(0).path("text").asText().contains("小学3年级"));
        }
    }
    @Test void java8HttpAdapterDoesNotFollowRedirectsOrLeakAuthorization()throws Exception {
        try(MockWebServer server=new MockWebServer();MockWebServer destination=new MockWebServer()) {
            server.start();destination.start();
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location",destination.url("/capture")));
            DashScopeProvider provider=new DashScopeProvider("test-secret",server.url("").toString(),"vision-test","speech-test");
            assertEquals("AI_REQUEST_REJECTED",assertThrows(ApiException.class,()->provider.lookup("apple",3)).code);
            assertEquals(0,destination.getRequestCount());
        }
    }
    @Test void java8HttpAdapterPreservesRateLimitAndResponseBounds()throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start();
            DashScopeProvider provider=new DashScopeProvider("test-secret",server.url("").toString(),"vision-test","speech-test");
            server.enqueue(new MockResponse().setResponseCode(429));
            assertEquals("AI_RATE_LIMITED",assertThrows(ApiException.class,()->provider.lookup("apple",3)).code);
            char[] large=new char[1024*1024+2];java.util.Arrays.fill(large,'x');
            server.enqueue(new MockResponse().setBody(new String(large)));
            assertEquals("AI_REQUEST_REJECTED",assertThrows(ApiException.class,()->provider.lookup("apple",3)).code);
        }
    }
}
