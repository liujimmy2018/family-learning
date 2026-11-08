package cn.family.learning;
import cn.family.learning.catalog.*;
import cn.family.learning.catalog.CatalogDtos.Mark;
import cn.family.learning.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ReviewRulesTest {
    private Mark mark(int round,int index,Boolean correct){Mark m=new Mark();m.round=round;m.index=index;m.correct=correct;return m;}
    @Test void requiresAllQuestionsAcrossAllRounds(){assertThrows(ApiException.class,()->ReviewRules.validate(Arrays.asList(mark(0,0,true)),2,2));}
    @Test void rejectsDuplicatesOutOfRangeAndUnmarkedAnswers(){assertThrows(ApiException.class,()->ReviewRules.validate(Arrays.asList(mark(0,0,true),mark(0,0,false)),2,1));assertThrows(ApiException.class,()->ReviewRules.validate(Arrays.asList(mark(1,0,true)),1,1));assertThrows(ApiException.class,()->ReviewRules.validate(Arrays.asList(mark(0,0,null)),1,1));}
    @Test void ordersMarksAndPreservesWrongAnswers(){Mark wrong=mark(0,1,false);wrong.written=" appl ";List<Mark> result=ReviewRules.validate(Arrays.asList(wrong,mark(0,0,true)),2,1);assertEquals(0,result.get(0).index);assertEquals("appl",result.get(1).written);}
    @Test void computesPercentageWithOneDecimalPlace(){assertEquals(new BigDecimal("66.7"),ReviewRules.score(2,3));assertEquals(new BigDecimal("0.0"),ReviewRules.score(0,3));assertEquals(new BigDecimal("100.0"),ReviewRules.score(3,3));}
}
