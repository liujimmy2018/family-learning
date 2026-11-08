package cn.family.learning.catalog;

import cn.family.learning.common.ApiException;
import java.util.*;
import java.math.*;
import static cn.family.learning.catalog.CatalogDtos.*;

public final class ReviewRules {
    private ReviewRules(){}
    public static List<Mark> validate(List<Mark> marks,int words,int rounds){
        if(words<1||rounds<1||marks==null||marks.size()!=words*rounds)throw ApiException.invalid("请逐题标记所有轮次的对错后再提交");
        Set<String> seen=new HashSet<>();List<Mark> ordered=new ArrayList<>(marks);
        for(Mark m:ordered){if(m==null||m.correct==null||m.round<0||m.round>=rounds||m.index<0||m.index>=words||!seen.add(m.round+":"+m.index))throw ApiException.invalid("批改题号重复、缺失或超出范围");m.written=CatalogRules.text(m.written,100,"孩子的拼写",true);}
        ordered.sort(Comparator.comparingInt((Mark m)->m.round).thenComparingInt(m->m.index));return ordered;
    }
    public static BigDecimal score(int correct,int total){if(total<1||correct<0||correct>total)throw ApiException.invalid("评分数量无效");return BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP);}
}
