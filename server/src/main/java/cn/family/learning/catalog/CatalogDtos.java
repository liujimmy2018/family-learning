package cn.family.learning.catalog;

import cn.family.learning.common.BmpText;
import cn.family.learning.learning.Dtos.Settings;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.*;

public final class CatalogDtos {
    private CatalogDtos() {}
    public static class Entry {
        public String id;
        @Min(1) @Max(6) public int grade;
        @NotNull @Pattern(regexp="上册|下册") public String semester;
        @BmpText @NotBlank @Size(max=40) public String unit;
        @BmpText @Size(max=300) public String chinese;
        @BmpText @NotBlank @Size(max=100) public String english;
        @BmpText @Size(max=200) public String phonetic="";
        public int ordinal;
    }
    public static class ImportInput {
        @BmpText @NotBlank @Size(max=100) public String title;
        @Valid @NotNull @Size(min=1,max=5000) public List<Entry> entries;
    }
    public static class EntryChange {
        @Min(1) public int expectedRevision;
        @Valid @NotNull public Entry entry;
    }
    public static class Rename {
        @Min(1) public int expectedRevision;
        @BmpText @NotBlank @Size(max=100) public String title;
    }
    public static class Selection {
        @NotBlank public String collectionId;
        @Min(1) public int expectedRevision;
        @Min(1) @Max(6) public int grade;
        @NotNull @Pattern(regexp="上册|下册") public String semester;
        @NotNull @Size(max=100) public List<@NotBlank @Size(max=40) String> units=new ArrayList<>();
        @NotNull @Pattern(regexp="ALL|RANDOM") public String selection="ALL";
        @Min(1) @Max(500) public int count=20;
        @Valid @NotNull public Settings settings;
    }
    public static class ReviewWord {
        public String id,english,chinese,phonetic="",semester="",unit="";
        public int grade,ordinal;
    }
    public static class Mark {
        @Min(0) public int round;
        @Min(0) public int index;
        @NotNull public Boolean correct;
        @BmpText @Size(max=100) public String written="";
    }
    public static class GradeInput {
        @Min(0) @Max(604800) public int elapsedSeconds;
        @Valid @NotNull @Size(min=1,max=1500) public List<Mark> marks;
    }
    public static class Timing {
        @Min(0) @Max(604800) public int elapsedSeconds;
    }
    public static class Mastery { @NotNull public Boolean mastered; }
}
