package dojo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGenTest {

    private static Problem problem(String number, String slug, String content, String meta, String examples) {
        return new Problem(number, slug, "标题", "Title", "中等", content,
                "class Solution {\n}", meta, examples, List.of());
    }

    @Test
    void generatesArrayProblem() {
        String meta = """
                {"name":"twoSum",
                 "params":[{"name":"nums","type":"integer[]"},{"name":"target","type":"integer"}],
                 "return":{"type":"integer[]"}}
                """;
        String examples = "[\"[2,7,11,15]\\n9\",\"[3,2,4]\\n6\"]";
        String content = """
                **示例 1：**

                ```
                输入：nums = [2,7,11,15], target = 9
                输出：[0,1]
                解释：因为 nums[0] + nums[1] == 9
                ```

                **示例 2：**

                ```
                输入：nums = [3,2,4], target = 6
                输出：[1,2]
                ```
                """;
        TestGen.Generated generated = TestGen.generate(
                problem("1", "two-sum", content, meta, examples), "p0001_two_sum", "TwoSumTest", false);
        assertNotNull(generated.source(), generated.note());
        String source = generated.source();
        assertTrue(source.contains("package p0001_two_sum;"), source);
        assertTrue(source.contains("class TwoSumTest"), source);
        assertTrue(source.contains("int[] nums = new int[]{2, 7, 11, 15};"), source);
        assertTrue(source.contains("int target = 9;"), source);
        assertTrue(source.contains("solution.twoSum(nums, target)"), source);
        assertTrue(source.contains("assertArrayEquals(new int[]{0, 1}, actual);"), source);
        assertTrue(source.contains("void officialExample2()"), source);
        assertTrue(source.contains("import static org.junit.jupiter.api.Assertions.*;"), source);
        assertEquals("已生成 2/2 个官方样例", generated.note());
    }

    @Test
    void parameterNamedLikeTheSolutionObjectDoesNotShadowIt() {
        String meta = """
                {"name":"lengthOfLongestSubstring",
                 "params":[{"name":"s","type":"string"}],
                 "return":{"type":"integer"}}
                """;
        String examples = "[\"\\\"abcabcbb\\\"\"]";
        String content = """
                ```
                输入：s = "abcabcbb"
                输出：3
                ```
                """;
        TestGen.Generated generated = TestGen.generate(
                problem("3", "longest-substring-without-repeating-characters", content, meta, examples),
                "p0003_x", "XTest", false);
        assertNotNull(generated.source(), generated.note());
        String source = generated.source();
        assertTrue(source.contains("String s = \"abcabcbb\";"), source);
        assertTrue(source.contains("solution.lengthOfLongestSubstring(s)"), source);
        assertFalse(source.contains("s.lengthOfLongestSubstring"), source);
    }

    @Test
    void wrapsLinkedListsWithKit() {
        String meta = """
                {"name":"addTwoNumbers",
                 "params":[{"name":"l1","type":"ListNode"},{"name":"l2","type":"ListNode"}],
                 "return":{"type":"ListNode"}}
                """;
        String examples = "[\"[2,4,3]\\n[5,6,4]\"]";
        String content = "```\n输入：l1 = [2,4,3], l2 = [5,6,4]\n输出：[7,0,8]\n```";
        String source = TestGen.generate(problem("2", "add-two-numbers", content, meta, examples),
                "p0002_add_two_numbers", "AddTwoNumbersTest", false).source();
        assertNotNull(source);
        assertTrue(source.contains("import kit.ListNode;"), source);
        assertTrue(source.contains("import kit.ListNodes;"), source);
        assertTrue(source.contains("ListNodes.of(new int[]{2, 4, 3})"), source);
        assertTrue(source.contains("assertArrayEquals(new int[]{7, 0, 8}, ListNodes.toArray(actual));"), source);
    }

    @Test
    void normalizesUnorderedNestedLists() {
        String meta = """
                {"name":"groupAnagrams",
                 "params":[{"name":"strs","type":"string[]"}],
                 "return":{"type":"list<list<string>>"}}
                """;
        String examples = "[\"[\\\"eat\\\",\\\"tea\\\",\\\"tan\\\",\\\"ate\\\",\\\"nat\\\",\\\"bat\\\"]\"]";
        String content = "```\n输入：strs = [\"eat\",\"tea\"]\n输出：[[\"bat\"],[\"nat\",\"tan\"],[\"ate\",\"eat\",\"tea\"]]\n```";
        String source = TestGen.generate(problem("49", "group-anagrams", content, meta, examples),
                "p0049_group_anagrams", "GroupAnagramsTest", false).source();
        assertNotNull(source);
        assertTrue(source.contains("norm("), source);
        assertTrue(source.contains("Collections.sort"), source);
        assertTrue(source.contains("new String[]{\"eat\", \"tea\", \"tan\", \"ate\", \"nat\", \"bat\"}"), source);
    }

    @Test
    void assertsMutatedArgumentForVoidProblems() {
        String meta = """
                {"name":"rotate",
                 "params":[{"name":"nums","type":"integer[]"},{"name":"k","type":"integer"}],
                 "return":{"type":"void"},
                 "output":{"paramindex":0}}
                """;
        String examples = "[\"[1,2,3,4,5,6,7]\\n3\"]";
        String content = "```\n输入：nums = [1,2,3,4,5,6,7], k = 3\n输出：[5,6,7,1,2,3,4]\n```";
        String source = TestGen.generate(problem("189", "rotate-array", content, meta, examples),
                "p0189_rotate_array", "RotateArrayTest", false).source();
        assertNotNull(source);
        assertTrue(source.contains("solution.rotate(nums, k);"), source);
        assertTrue(source.contains("assertArrayEquals(new int[]{5, 6, 7, 1, 2, 3, 4}, nums);"), source);
    }

    @Test
    void generatesDesignProblemFromOperationSequence() {
        String meta = """
                {"classname":"LRUCache",
                 "constructor":{"params":[{"name":"capacity","type":"integer"}]},
                 "methods":[
                   {"name":"get","params":[{"name":"key","type":"integer"}],"return":{"type":"integer"}},
                   {"name":"put","params":[{"name":"key","type":"integer"},{"name":"value","type":"integer"}],
                    "return":{"type":"void"}}]}
                """;
        String examples = "[\"[\\\"LRUCache\\\",\\\"put\\\",\\\"get\\\",\\\"put\\\"]\\n[[2],[1,1],[1],[2,2]]\"]";
        // 真实题面（leetcode.cn 146）是「输入」「输出」各占一行，没有冒号。
        String content = "```\n输入\n[\"LRUCache\",\"put\",\"get\",\"put\"]\n"
                + "[[2],[1,1],[1],[2,2]]\n输出\n[null,null,1,null]\n解释\nLRUCache lRUCache = new LRUCache(2);\n```";
        TestGen.Generated generated = TestGen.generate(problem("146", "lru-cache", content, meta, examples),
                "p0146_lru_cache", "LruCacheTest", false);
        assertNotNull(generated.source(), generated.note());
        String source = generated.source();
        assertTrue(source.contains("LRUCache obj = new LRUCache(2);"), source);
        assertTrue(source.contains("obj.put(1, 1);"), source);
        assertTrue(source.contains("assertEquals(1, obj.get(1));"), source);
        assertTrue(source.contains("class LruCacheTest"), source);
        assertTrue(!source.contains("private final Solution s"), source);
    }

    @Test
    void skipsWhenExpectedOutputsAreMissing() {
        String meta = """
                {"name":"solve","params":[{"name":"n","type":"integer"}],"return":{"type":"integer"}}
                """;
        TestGen.Generated generated = TestGen.generate(
                problem("9", "weird", "没有示例输出", meta, "[\"1\"]"), "p0009_weird", "WeirdTest", false);
        assertNull(generated.source());
        assertTrue(generated.note().contains("输出"), generated.note());
    }

    @Test
    void explainsUnsupportedTypes() {
        String meta = """
                {"name":"cloneGraph","params":[{"name":"node","type":"Node"}],"return":{"type":"Node"}}
                """;
        String content = "```\n输入：adjList = [[2,4]]\n输出：[[2,4]]\n```";
        TestGen.Generated generated = TestGen.generate(
                problem("133", "clone-graph", content, meta, "[\"[[2,4]]\"]"), "p0133_clone_graph", "CloneGraphTest", false);
        assertNull(generated.source());
        assertTrue(generated.note().contains("还不支持类型"), generated.note());
    }

    @Test
    void extractsExpectedOutputsFromFencesOnly() {
        String content = """
                描述里也提到输出：99 这种字样，但示例在下面。

                ```
                输入：nums = [1]
                输出：[1]
                ```

                ```
                输入：nums = [2]
                输出：[2]
                ```
                """;
        assertEquals(List.of("[1]", "[2]"), TestGen.expectedOutputs(content));
    }

    @Test
    void matchesExpectedOutputByInputWhenApiTrimsExamples() {
        String meta = """
                {"name":"romanToInt",
                 "params":[{"name":"s","type":"string"}],
                 "return":{"type":"integer"}}
                """;
        String examples = "[\"\\\"III\\\"\",\"\\\"LVIII\\\"\",\"\\\"MCMXCIV\\\"\"]";
        String content = """
                ```
                **示例 1:**
                输入: s = "III"
                输出: 3

                **示例 2:**
                输入: s = "IV"
                输出: 4

                **示例 3:**
                输入: s = "IX"
                输出: 9

                **示例 4:**
                输入: s = "LVIII"
                输出: 58

                **示例 5:**
                输入: s = "MCMXCIV"
                输出: 1994
                ```
                """;
        String source = TestGen.generate(problem("13", "roman-to-integer", content, meta, examples),
                "p0013_roman_to_integer", "RomanToIntegerTest", false).source();
        assertTrue(source.contains("assertEquals(3, actual)"), source);
        assertTrue(source.contains("assertEquals(58, actual)"), source);
        assertTrue(source.contains("assertEquals(1994, actual)"), source);
        assertFalse(source.contains("assertEquals(4, actual)"), source);
        assertFalse(source.contains("assertEquals(9, actual)"), source);
    }

    @Test
    void treatsOrderFreeProblemAsUnordered() {
        String meta = """
                {"name":"threeSum",
                 "params":[{"name":"nums","type":"integer[]"}],
                 "return":{"type":"list<list<integer>>"}}
                """;
        String examples = "[\"[-1,0,1,2,-1,-4]\",\"[0,1,1]\"]";
        String content = """
                ```
                输入：nums = [-1,0,1,2,-1,-4]
                输出：[[-1,-1,2],[-1,0,1]]

                输入：nums = [0,1,1]
                输出：[]
                ```
                注意，输出的顺序和三元组的顺序并不重要。
                """;
        String source = TestGen.generate(problem("15", "3sum", content, meta, examples),
                "p0015_3sum", "P3sumTest", false).source();
        assertNotNull(source);
        assertTrue(source.contains("normInts("), source);
        assertTrue(source.contains("private static List<List<Integer>> normInts"), source);
    }

    @Test
    void keepsStrictOrderWhenProblemSaysNothing() {
        String meta = """
                {"name":"generate",
                 "params":[{"name":"numRows","type":"integer"}],
                 "return":{"type":"list<list<integer>>"}}
                """;
        String examples = "[\"5\"]";
        String content = "```\n输入：numRows = 5\n输出：[[1],[1,1],[1,2,1],[1,3,3,1],[1,4,6,4,1]]\n```";
        String source = TestGen.generate(problem("118", "pascals-triangle", content, meta, examples),
                "p0118_pascals_triangle", "PascalsTriangleTest", false).source();
        assertNotNull(source);
        assertFalse(source.contains("normInts("), source);
    }

    @Test
    void refusesWhenSignatureHasMoreParametersThanMetadata() {
        String meta = """
                {"name":"hasCycle",
                 "params":[{"name":"head","type":"ListNode"},{"name":"pos","type":"integer"}],
                 "return":{"type":"boolean"}}
                """;
        String snippet = """
                public class Solution {
                    public boolean hasCycle(ListNode head) {
                        return false;
                    }
                }
                """;
        String source = TestGen.generate(problemWithSnippet("141", "linked-list-cycle", meta, snippet),
                "p0141_linked_list_cycle", "LinkedListCycleTest", false).source();
        assertNull(source);
    }

    @Test
    void refusesCustomTypeUnknownToTheGenerator() {
        String meta = """
                {"name":"connect","params":[{"name":"root","type":"TreeNode"}],
                 "return":{"type":"TreeNode"}}
                """;
        String snippet = """
                class Node {
                    public int val;
                }
                public class Solution {
                    public Node connect(Node root) {
                        return root;
                    }
                }
                """;
        String generated = TestGen.generate(problemWithSnippet("116", "x", meta, snippet),
                "p0116_x", "XTest", false).source();
        assertNull(generated);
    }

    @Test
    void acceptsSignatureThatMatchesMetadata() {
        String meta = """
                {"name":"twoSum","params":[{"name":"nums","type":"integer[]"},
                 {"name":"target","type":"integer"}],"return":{"type":"integer[]"}}
                """;
        String snippet = """
                class Solution {
                    public int[] twoSum(int[] nums, int target) {
                        return null;
                    }
                }
                """;
        Problem problem = new Problem("1", "two-sum", "两数之和", "Two Sum", "Easy",
                "```\n输入：nums = [2,7,11,15], target = 9\n输出：[0,1]\n```\n```\n输入：nums = [3,2,4], target = 6\n输出：[1,2]\n```",
                snippet, meta, "[\"[2,7,11,15]\\n9\",\"[3,2,4]\\n6\"]", java.util.List.of("数组"));
        TestGen.Generated generated = TestGen.generate(problem, "p0001_two_sum", "TwoSumTest", false);
        assertNotNull(generated.source(), generated.note());
    }

    private static Problem problemWithSnippet(String id, String slug, String meta, String snippet) {
        return new Problem(id, slug, "t", "T", "Medium",
                "```\n输入：1\n输出：1\n```", snippet, meta, "[\"1\"]", java.util.List.of());
    }
}
