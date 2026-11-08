package cn.family.learning.catalog;

import cn.family.learning.auth.Tokens;
import cn.family.learning.common.*;
import java.util.*;
import static cn.family.learning.catalog.CatalogDtos.*;

public final class CatalogRules {
    private CatalogRules() {}
    public static String practiceTitle(String bookTitle,Selection selection){
        List<String> units=new ArrayList<>(new LinkedHashSet<>(selection.units));
        String scope=units.isEmpty()?"整册":String.join("、",units);
        if(scope.length()>52)scope=units.get(0)+" 等"+units.size()+"个单元";
        String suffix=" · "+selection.grade+"年级"+selection.semester+" · "+scope;
        return bookTitle.substring(0,Math.min(bookTitle.length(),100-suffix.length()))+suffix;
    }
    public static String wordKey(String english){return Tokens.hash(english.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," "));}
    public static String text(String value,int max,String label,boolean optional){
        String s=value==null?"":value.trim();
        if((!optional&&s.isEmpty())||s.length()>max||!BmpText.Validator.supported(s))throw ApiException.invalid(label+"不能为空、超长或包含不支持的文字");
        return s;
    }
    public static Entry entry(Entry e){
        return entry(e, false);
    }
    public static Entry entry(Entry e, boolean chinese){
        if(e==null||e.grade<1||e.grade>6)throw ApiException.invalid("年级需为1—6");
        if(!"上册".equals(e.semester)&&!"下册".equals(e.semester))throw ApiException.invalid("上下册只能填写上册或下册");
        if(chinese){
            e.unit=text(e.unit,40,"所属单元",false);
            e.english=text(e.english,100,"生字词",false);
            if(!e.english.matches("\\p{IsHan}+"))throw ApiException.invalid("生字词只能填写汉字");
            e.phonetic=text(e.phonetic,200,"拼音",false);
            if(!e.phonetic.matches("[\\p{IsLatin}\\p{M}1-5 '’üÜ:·-]+")||!e.phonetic.matches(".*\\p{IsLatin}.*"))
                throw ApiException.invalid("拼音请填写字母和声调，例如 chūn tiān 或 chun1 tian1");
            e.chinese=e.english;
            return e;
        }
        e.unit=text(e.unit,40,"所属单元",false);e.chinese=text(e.chinese,300,"中文",false);
        e.english=text(e.english,100,"英文",false);e.phonetic=text(e.phonetic,200,"音标",true);
        if(!e.english.matches(".*[A-Za-z].*"))throw ApiException.invalid("英文栏至少包含一个英文字母");
        return e;
    }
    public static void unique(List<Entry> entries){
        unique(entries,false);
    }
    public static void unique(List<Entry> entries, boolean chinese){
        Set<String> keys=new HashSet<>();
        for(Entry e:entries){
            entry(e,chinese);
            String key=e.grade+"|"+e.semester+"|"+e.unit+"|"+wordKey(e.english);
            if(!keys.add(key))throw ApiException.invalid("同一年级、上下册和单元内存在重复词条："+e.english);
        }
    }
}
