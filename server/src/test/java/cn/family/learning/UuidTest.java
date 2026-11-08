package cn.family.learning;

import cn.family.learning.common.Ids;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UuidTest {
    @Test void generatedIdsAreCanonicalVersionFourUuids() {
        Set<String> values=new HashSet<>();
        for(int n=0;n<1000;n++) {
            String value=Ids.next();UUID uuid=UUID.fromString(value);
            assertEquals(4,uuid.version());assertEquals(2,uuid.variant());assertEquals(uuid.toString(),Ids.require(value));
            assertTrue(values.add(value));
        }
    }
    @Test void numericAndShortenedUuidRepresentationsAreRejected() {
        for(String value:Arrays.asList("1","001","1-1-1-1-1","not-a-uuid","550E8400-E29B-41D4-A716-446655440000",""))
            assertThrows(cn.family.learning.common.ApiException.class,()->Ids.require(value));
    }
}
