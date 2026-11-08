package cn.family.learning.learning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import cn.family.learning.common.BmpText;
import cn.family.learning.common.Ids;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

public final class Dtos {
    private Dtos() {}
    public static final class ChildInput {
        public final @BmpText @NotBlank @Size(max=30) String nickname;
        public final @Min(1) @Max(6) int grade;
        public final @BmpText @NotNull @Size(max=80) String textbook;
        public final @Min(0) long expectedVersion;
        @JsonCreator
        public ChildInput(@JsonProperty("nickname") String nickname, @JsonProperty("grade") int grade, @JsonProperty("textbook") String textbook, @JsonProperty("expectedVersion") long expectedVersion) {
            this.nickname = nickname;
            this.grade = grade;
            this.textbook = textbook;
            this.expectedVersion = expectedVersion;
        }
        public String nickname() { return nickname; }
        public int grade() { return grade; }
        public String textbook() { return textbook; }
        public long expectedVersion() { return expectedVersion; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ChildInput value = (ChildInput) other;
            return java.util.Objects.equals(nickname, value.nickname) && java.util.Objects.equals(grade, value.grade) && java.util.Objects.equals(textbook, value.textbook) && java.util.Objects.equals(expectedVersion, value.expectedVersion);
        }
        @Override public int hashCode() { return java.util.Objects.hash(nickname, grade, textbook, expectedVersion); }
    }
    public static final class ChildDto {
        public final String id;
        public final String nickname;
        public final int grade;
        public final String textbook;
        public final long version;
        @JsonCreator
        public ChildDto(@JsonProperty("id") String id, @JsonProperty("nickname") String nickname, @JsonProperty("grade") int grade, @JsonProperty("textbook") String textbook, @JsonProperty("version") long version) {
            this.id = id;
            this.nickname = nickname;
            this.grade = grade;
            this.textbook = textbook;
            this.version = version;
        }
        public String id() { return id; }
        public String nickname() { return nickname; }
        public int grade() { return grade; }
        public String textbook() { return textbook; }
        public long version() { return version; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ChildDto value = (ChildDto) other;
            return java.util.Objects.equals(id, value.id) && java.util.Objects.equals(nickname, value.nickname) && java.util.Objects.equals(grade, value.grade) && java.util.Objects.equals(textbook, value.textbook) && java.util.Objects.equals(version, value.version);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, nickname, grade, textbook, version); }
    }
    public static final class WordInput {
        public final @BmpText @NotBlank @Size(max=100) String text;
        public final @BmpText @NotBlank @Size(max=300) String meaning;
        @JsonCreator
        public WordInput(@JsonProperty("text") String text, @JsonProperty("meaning") String meaning) {
            this.text = text;
            this.meaning = meaning;
        }
        public String text() { return text; }
        public String meaning() { return meaning; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            WordInput value = (WordInput) other;
            return java.util.Objects.equals(text, value.text) && java.util.Objects.equals(meaning, value.meaning);
        }
        @Override public int hashCode() { return java.util.Objects.hash(text, meaning); }
    }
    public static final class WordDto {
        public final String id;
        public final int ordinal;
        public final String text;
        public final String meaning;
        @JsonCreator
        public WordDto(@JsonProperty("id") String id, @JsonProperty("ordinal") int ordinal, @JsonProperty("text") String text, @JsonProperty("meaning") String meaning) {
            this.id = id;
            this.ordinal = ordinal;
            this.text = text;
            this.meaning = meaning;
        }
        public String id() { return id; }
        public int ordinal() { return ordinal; }
        public String text() { return text; }
        public String meaning() { return meaning; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            WordDto value = (WordDto) other;
            return java.util.Objects.equals(id, value.id) && java.util.Objects.equals(ordinal, value.ordinal) && java.util.Objects.equals(text, value.text) && java.util.Objects.equals(meaning, value.meaning);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, ordinal, text, meaning); }
    }
    public static final class ListInput {
        public final @BmpText @NotBlank @Size(max=100) String title;
        public final @NotEmpty @Size(max=500) List<@Valid WordInput> words;
        public final String recognitionJobId;
        public final @Min(0) int expectedVersion;
        @JsonCreator
        public ListInput(@JsonProperty("title") String title, @JsonProperty("words") List<WordInput> words, @JsonProperty("recognitionJobId") String recognitionJobId, @JsonProperty("expectedVersion") int expectedVersion) {
            this.title = title;
            this.words = words;
            this.recognitionJobId = recognitionJobId;
            this.expectedVersion = expectedVersion;
        }
        public String title() { return title; }
        public List<WordInput> words() { return words; }
        public String recognitionJobId() { return recognitionJobId; }
        public int expectedVersion() { return expectedVersion; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ListInput value = (ListInput) other;
            return java.util.Objects.equals(title, value.title) && java.util.Objects.equals(words, value.words) && java.util.Objects.equals(recognitionJobId, value.recognitionJobId) && java.util.Objects.equals(expectedVersion, value.expectedVersion);
        }
        @Override public int hashCode() { return java.util.Objects.hash(title, words, recognitionJobId, expectedVersion); }
    }
    public static final class ListDto {
        public final String id;
        public final String title;
        public final int version;
        public final List<WordDto> words;
        @JsonCreator
        public ListDto(@JsonProperty("id") String id, @JsonProperty("title") String title, @JsonProperty("version") int version, @JsonProperty("words") List<WordDto> words) {
            this.id = id;
            this.title = title;
            this.version = version;
            this.words = words;
        }
        public String id() { return id; }
        public String title() { return title; }
        public int version() { return version; }
        public List<WordDto> words() { return words; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ListDto value = (ListDto) other;
            return java.util.Objects.equals(id, value.id) && java.util.Objects.equals(title, value.title) && java.util.Objects.equals(version, value.version) && java.util.Objects.equals(words, value.words);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, title, version, words); }
    }
    public static final class MaterialInput {
        public final @BmpText @NotBlank @Size(max=100) String title;
        public final @Pattern(regexp="WORD_TABLE|TEXTBOOK") @NotNull String type;
        public final @NotEmpty @Size(max=5) List<@Pattern(regexp=Ids.PATTERN) String> assetIds;
        @JsonCreator
        public MaterialInput(@JsonProperty("title") String title, @JsonProperty("type") String type, @JsonProperty("assetIds") List<String> assetIds) {
            this.title = title;
            this.type = type;
            this.assetIds = assetIds;
        }
        public String title() { return title; }
        public String type() { return type; }
        public List<String> assetIds() { return assetIds; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            MaterialInput value = (MaterialInput) other;
            return java.util.Objects.equals(title, value.title) && java.util.Objects.equals(type, value.type) && java.util.Objects.equals(assetIds, value.assetIds);
        }
        @Override public int hashCode() { return java.util.Objects.hash(title, type, assetIds); }
    }
    public static final class Settings {
        public final @Pattern(regexp="ENGLISH|CHINESE") @NotNull String mode;
        public final @Min(0) @Max(30) int countdownSeconds;
        public final @Min(1) @Max(120) int intervalSeconds;
        public final @Min(1) @Max(5) int repeats;
        public final @DecimalMin("0.5") @DecimalMax("3") double repeatGapSeconds;
        public final @Min(1) @Max(3) int rounds;
        public final boolean randomOrder;
        @JsonCreator
        public Settings(@JsonProperty("mode") String mode, @JsonProperty("countdownSeconds") int countdownSeconds, @JsonProperty("intervalSeconds") int intervalSeconds, @JsonProperty("repeats") int repeats, @JsonProperty("repeatGapSeconds") double repeatGapSeconds, @JsonProperty("rounds") int rounds, @JsonProperty("randomOrder") boolean randomOrder) {
            this.mode = mode;
            this.countdownSeconds = countdownSeconds;
            this.intervalSeconds = intervalSeconds;
            this.repeats = repeats;
            this.repeatGapSeconds = repeatGapSeconds;
            this.rounds = rounds;
            this.randomOrder = randomOrder;
        }
        public String mode() { return mode; }
        public int countdownSeconds() { return countdownSeconds; }
        public int intervalSeconds() { return intervalSeconds; }
        public int repeats() { return repeats; }
        public double repeatGapSeconds() { return repeatGapSeconds; }
        public int rounds() { return rounds; }
        public boolean randomOrder() { return randomOrder; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Settings value = (Settings) other;
            return java.util.Objects.equals(mode, value.mode) && java.util.Objects.equals(countdownSeconds, value.countdownSeconds) && java.util.Objects.equals(intervalSeconds, value.intervalSeconds) && java.util.Objects.equals(repeats, value.repeats) && java.util.Objects.equals(repeatGapSeconds, value.repeatGapSeconds) && java.util.Objects.equals(rounds, value.rounds) && java.util.Objects.equals(randomOrder, value.randomOrder);
        }
        @Override public int hashCode() { return java.util.Objects.hash(mode, countdownSeconds, intervalSeconds, repeats, repeatGapSeconds, rounds, randomOrder); }
    }
    public static final class PracticeInput {
        public final @Pattern(regexp=Ids.PATTERN) @NotNull String listId;
        public final @Min(1) int expectedListVersion;
        public final @NotEmpty @Size(max=100) List<@Pattern(regexp=Ids.PATTERN) String> itemIds;
        public final @NotNull @Valid Settings settings;
        @JsonCreator
        public PracticeInput(@JsonProperty("listId") String listId, @JsonProperty("expectedListVersion") int expectedListVersion, @JsonProperty("itemIds") List<String> itemIds, @JsonProperty("settings") Settings settings) {
            this.listId = listId;
            this.expectedListVersion = expectedListVersion;
            this.itemIds = itemIds;
            this.settings = settings;
        }
        public String listId() { return listId; }
        public int expectedListVersion() { return expectedListVersion; }
        public List<String> itemIds() { return itemIds; }
        public Settings settings() { return settings; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            PracticeInput value = (PracticeInput) other;
            return java.util.Objects.equals(listId, value.listId) && java.util.Objects.equals(expectedListVersion, value.expectedListVersion) && java.util.Objects.equals(itemIds, value.itemIds) && java.util.Objects.equals(settings, value.settings);
        }
        @Override public int hashCode() { return java.util.Objects.hash(listId, expectedListVersion, itemIds, settings); }
    }
    public static final class SnapshotWord {
        public final String id;
        public final int ordinal;
        public final String text;
        public final String meaning;
        public final String phonetic;
        public SnapshotWord(String id,int ordinal,String text,String meaning) {
            this(id,ordinal,text,meaning,"");
        }
        @JsonCreator
        public SnapshotWord(@JsonProperty("id") String id, @JsonProperty("ordinal") int ordinal, @JsonProperty("text") String text, @JsonProperty("meaning") String meaning, @JsonProperty("phonetic") String phonetic) {
            this.id = id;
            this.ordinal = ordinal;
            this.text = text;
            this.meaning = meaning;
            this.phonetic = phonetic==null?"":phonetic;
        }
        public String id() { return id; }
        public int ordinal() { return ordinal; }
        public String text() { return text; }
        public String meaning() { return meaning; }
        public String phonetic() { return phonetic; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            SnapshotWord value = (SnapshotWord) other;
            return java.util.Objects.equals(id, value.id) && java.util.Objects.equals(ordinal, value.ordinal) && java.util.Objects.equals(text, value.text) && java.util.Objects.equals(meaning, value.meaning) && java.util.Objects.equals(phonetic, value.phonetic);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, ordinal, text, meaning, phonetic); }
    }
    public static final class PracticeDto {
        public final String id;
        public final String title;
        public final List<SnapshotWord> words;
        public final Settings settings;
        public final String status;
        public final long version;
        public final Map<String,Object> progress;
        public final String subject;
        @JsonCreator
        public PracticeDto(@JsonProperty("id") String id, @JsonProperty("title") String title, @JsonProperty("words") List<SnapshotWord> words, @JsonProperty("settings") Settings settings, @JsonProperty("status") String status, @JsonProperty("version") long version, @JsonProperty("progress") Map<String,Object> progress,@JsonProperty("subject") String subject) {
            this.id = id;
            this.title = title;
            this.words = words;
            this.settings = settings;
            this.status = status;
            this.version = version;
            this.progress = progress;
            this.subject = subject==null?"ENGLISH":subject;
        }
        public String id() { return id; }
        public String title() { return title; }
        public List<SnapshotWord> words() { return words; }
        public Settings settings() { return settings; }
        public String status() { return status; }
        public long version() { return version; }
        public Map<String,Object> progress() { return progress; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            PracticeDto value = (PracticeDto) other;
            return java.util.Objects.equals(id, value.id) && java.util.Objects.equals(title, value.title) && java.util.Objects.equals(words, value.words) && java.util.Objects.equals(settings, value.settings) && java.util.Objects.equals(status, value.status) && java.util.Objects.equals(version, value.version) && java.util.Objects.equals(progress, value.progress) && java.util.Objects.equals(subject, value.subject);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, title, words, settings, status, version, progress, subject); }
    }
    public static final class Event {
        public final @Pattern(regexp="[a-fA-F0-9-]{36}") @NotNull String eventId;
        public final @Pattern(regexp="PLAY|PAUSE|PREVIOUS|NEXT|REPLAY|RESUME|COMPLETE|STOP") @NotNull String action;
        public final @Min(0) int round;
        public final @Min(0) int itemIndex;
        public final long occurredAt;
        @JsonCreator
        public Event(@JsonProperty("eventId") String eventId, @JsonProperty("action") String action, @JsonProperty("round") int round, @JsonProperty("itemIndex") int itemIndex, @JsonProperty("occurredAt") long occurredAt) {
            this.eventId = eventId;
            this.action = action;
            this.round = round;
            this.itemIndex = itemIndex;
            this.occurredAt = occurredAt;
        }
        public String eventId() { return eventId; }
        public String action() { return action; }
        public int round() { return round; }
        public int itemIndex() { return itemIndex; }
        public long occurredAt() { return occurredAt; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Event value = (Event) other;
            return java.util.Objects.equals(eventId, value.eventId) && java.util.Objects.equals(action, value.action) && java.util.Objects.equals(round, value.round) && java.util.Objects.equals(itemIndex, value.itemIndex) && java.util.Objects.equals(occurredAt, value.occurredAt);
        }
        @Override public int hashCode() { return java.util.Objects.hash(eventId, action, round, itemIndex, occurredAt); }
    }
    public static final class ProgressInput {
        public final @Min(1) long expectedVersion;
        public final @Pattern(regexp="READY|PAUSED|COMPLETED|STOPPED") @NotNull String status;
        public final @Min(0) int round;
        public final @Min(0) int itemIndex;
        public final @Size(max=1000) @NotNull List<@Valid Event> events;
        @JsonCreator
        public ProgressInput(@JsonProperty("expectedVersion") long expectedVersion, @JsonProperty("status") String status, @JsonProperty("round") int round, @JsonProperty("itemIndex") int itemIndex, @JsonProperty("events") List<Event> events) {
            this.expectedVersion = expectedVersion;
            this.status = status;
            this.round = round;
            this.itemIndex = itemIndex;
            this.events = events;
        }
        public long expectedVersion() { return expectedVersion; }
        public String status() { return status; }
        public int round() { return round; }
        public int itemIndex() { return itemIndex; }
        public List<Event> events() { return events; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ProgressInput value = (ProgressInput) other;
            return java.util.Objects.equals(expectedVersion, value.expectedVersion) && java.util.Objects.equals(status, value.status) && java.util.Objects.equals(round, value.round) && java.util.Objects.equals(itemIndex, value.itemIndex) && java.util.Objects.equals(events, value.events);
        }
        @Override public int hashCode() { return java.util.Objects.hash(expectedVersion, status, round, itemIndex, events); }
    }
    public static final class SpeechInput {
        public final @BmpText @NotBlank @Size(max=300) String text;
        public final @Pattern(regexp="en-US|zh-CN") @NotNull String language;
        @JsonCreator
        public SpeechInput(@JsonProperty("text") String text, @JsonProperty("language") String language) {
            this.text = text;
            this.language = language;
        }
        public String text() { return text; }
        public String language() { return language; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            SpeechInput value = (SpeechInput) other;
            return java.util.Objects.equals(text, value.text) && java.util.Objects.equals(language, value.language);
        }
        @Override public int hashCode() { return java.util.Objects.hash(text, language); }
    }
    public static final class LookupInput {
        public final @BmpText @NotBlank @Size(max=100) String text;
        @JsonCreator
        public LookupInput(@JsonProperty("text") String text) {
            this.text = text;
        }
        public String text() { return text; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            LookupInput value = (LookupInput) other;
            return java.util.Objects.equals(text, value.text);
        }
        @Override public int hashCode() { return java.util.Objects.hash(text); }
    }
    public static final class JobDto {
        public final String id;
        public final String status;
        public final String type;
        public final Object result;
        public final String errorCode;
        public final String errorMessage;
        @JsonCreator
        public JobDto(@JsonProperty("id") String id, @JsonProperty("status") String status, @JsonProperty("type") String type, @JsonProperty("result") Object result, @JsonProperty("errorCode") String errorCode, @JsonProperty("errorMessage") String errorMessage) {
            this.id = id;
            this.status = status;
            this.type = type;
            this.result = result;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        public String id() { return id; }
        public String status() { return status; }
        public String type() { return type; }
        public Object result() { return result; }
        public String errorCode() { return errorCode; }
        public String errorMessage() { return errorMessage; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            JobDto value = (JobDto) other;
            return java.util.Objects.equals(id, value.id) && java.util.Objects.equals(status, value.status) && java.util.Objects.equals(type, value.type) && java.util.Objects.equals(result, value.result) && java.util.Objects.equals(errorCode, value.errorCode) && java.util.Objects.equals(errorMessage, value.errorMessage);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, status, type, result, errorCode, errorMessage); }
    }
    public static final class AssetDto {
        public final String id;
        public final String contentType;
        public final long sizeBytes;
        public final int width;
        public final int height;
        @JsonCreator
        public AssetDto(@JsonProperty("id") String id, @JsonProperty("contentType") String contentType, @JsonProperty("sizeBytes") long sizeBytes, @JsonProperty("width") int width, @JsonProperty("height") int height) {
            this.id = id;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.width = width;
            this.height = height;
        }
        public String id() { return id; }
        public String contentType() { return contentType; }
        public long sizeBytes() { return sizeBytes; }
        public int width() { return width; }
        public int height() { return height; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            AssetDto value = (AssetDto) other;
            return java.util.Objects.equals(id, value.id) && java.util.Objects.equals(contentType, value.contentType) && java.util.Objects.equals(sizeBytes, value.sizeBytes) && java.util.Objects.equals(width, value.width) && java.util.Objects.equals(height, value.height);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, contentType, sizeBytes, width, height); }
    }
}
