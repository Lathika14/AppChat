
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Reproduce {
    public static void main(String[] args) {
        test("Simple", "{\"user\":\"Lathika\",\"content\":\"Simple\"}");
        test("With Quote", "{\"user\":\"Lathika\",\"content\":\"With \\\"Quote\\\"\"}");
        test("With Backslash", "{\"user\":\"Lathika\",\"content\":\"With \\\\ Backslash\"}");

        // Simulating the bug I suspect:
        // Input JSON for "With \"Quote\"" is {"content":"With \"Quote\""} where " is
        // escaped as \"
        // My manual extraction
    }

    private static void test(String name, String json) {
        System.out.println("--- Test: " + name + " ---");
        System.out.println("JSON: " + json);
        String user = extractJsonValue(json, "user");
        String content = extractJsonValue(json, "content");
        System.out.println("Extracted User: " + user);
        System.out.println("Extracted Content: " + content);

        // Now test broadcast construction
        String safeContent = content.replace("\"", "\\\"");
        String jsonBroadcast = String.format(
                "{\"user\": \"%s\", \"content\": \"%s\"}",
                user, safeContent);
        System.out.println("Broadcast JSON: " + jsonBroadcast);
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
