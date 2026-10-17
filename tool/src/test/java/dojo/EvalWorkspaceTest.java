package dojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EvalWorkspaceTest {

    @Test
    void takesLastJavaFence() {
        String reply = """
                先说明思路。

                ```java
                class Solution { int a() { return 1; } }
                ```

                再给完整版：

                ```java
                class Solution { int a() { return 2; } }
                ```
                """;
        String code = EvalWorkspace.extractJava(reply);
        assertTrue(code.contains("return 2"));
    }

    @Test
    void ignoresNonJavaFence() {
        String reply = "```\n输入：nums = [1,2]\n```\n\n```java\nclass Solution { }\n```";
        assertTrue(EvalWorkspace.extractJava(reply).startsWith("class Solution"));
    }

    @Test
    void returnsNullForPseudoCodeOnly() {
        assertNull(EvalWorkspace.extractJava("先建表，再遍历，最后返回。"));

        assertNull(EvalWorkspace.extractJava(""));
    }

    @Test
    void fallsBackToBareJava() {
        String code = "class Solution {\n    int f() { return 1; }\n}";
        assertEquals(code, EvalWorkspace.extractJava(code));
    }

    @Test
    void verdictClassifiesFailures() {
        assertEquals("通过", new EvalWorkspace.Run(0, "Tests run: 3, Failures: 0", 10).verdict());
        assertEquals("编译失败", new EvalWorkspace.Run(1, "[ERROR] COMPILATION ERROR", 10).verdict());
        assertEquals("运行时异常", new EvalWorkspace.Run(1, "Tests run: 2, Failures: 0, Errors: 2", 10).verdict());
        assertEquals("断言失败", new EvalWorkspace.Run(1, "Tests run: 3, Failures: 1, Errors: 0", 10).verdict());
        assertEquals("构建失败", new EvalWorkspace.Run(1, "BUILD FAILURE", 10).verdict());
    }
}
