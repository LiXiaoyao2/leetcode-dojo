package dojo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldTest {

    private static Problem problem(String slug, String snippet) {
        return new Problem("206", slug, "反转链表", "Reverse Linked List", "简单",
                "题面", snippet, null, null, List.of());
    }

    @Test
    void stripsTemplateCommentAndAddsKitImports() {
        String snippet = """
                /**
                 * Definition for singly-linked list.
                 * public class ListNode {
                 *     int val;
                 *     ListNode next;
                 * }
                 */
                class Solution {
                    public ListNode reverseList(ListNode head) {
                        return null;
                    }
                }
                """;
        String source = Scaffold.solutionSource(problem("reverse-linked-list", snippet), "p0206_reverse_linked_list");
        assertTrue(source.startsWith("package p0206_reverse_linked_list;"), source);
        assertTrue(source.contains("import kit.ListNode;"), source);
        assertFalse(source.contains("Definition for singly-linked list"), source);
        assertTrue(source.contains("class Solution {"), source);
        assertTrue(source.contains("// 0206"), source);
        assertTrue(source.contains("https://leetcode.cn/problems/reverse-linked-list/"), source);
    }

    @Test
    void doesNotImportKitWhenTemplateDeclaresItsOwnNode() {
        String snippet = """
                class Solution {
                    public ListNode merge(ListNode a) {
                        return a;
                    }
                }

                class ListNode {
                    int val;
                    ListNode next;
                }
                """;
        String source = Scaffold.solutionSource(problem("merge", snippet), "p0000_merge");
        assertFalse(source.contains("import kit.ListNode;"), source);
    }

    @Test
    void addsJavaUtilWhenTemplateUsesCollections() {
        String snippet = """
                class Solution {
                    public int[] topK(int[] nums, int k) {
                        Map<Integer, Integer> count = new HashMap<>();
                        return new int[0];
                    }
                }
                """;
        String source = Scaffold.solutionSource(problem("top-k", snippet), "p0000_top_k");
        assertTrue(source.contains("import java.util.*;"), source);
    }

    @Test
    void keepsDesignProblemTemplateAsIs() {
        String snippet = """
                class LRUCache {

                    public LRUCache(int capacity) {

                    }

                    public int get(int key) {
                        return 0;
                    }
                }
                """;
        String source = Scaffold.solutionSource(problem("lru-cache", snippet), "p0146_lru_cache");
        assertTrue(source.contains("class LRUCache {"), source);
        assertFalse(source.contains("import java.util.*;"), source);
    }

    @Test
    void stubsEmptyMethodBodiesSoTheModuleStillCompiles() {
        String snippet = """
                class Solution {
                    public int[] twoSum(int[] nums, int target) {

                    }
                }
                """;
        String source = Scaffold.solutionSource(problem("two-sum", snippet), "p0001_two_sum");
        assertTrue(source.contains("throw new UnsupportedOperationException(\"TODO: 还没写\");"), source);
    }

    @Test
    void fallsBackWhenTemplateIsMissing() {
        String source = Scaffold.solutionSource(problem("empty", null), "p0000_empty");
        assertTrue(source.contains("class Solution {"), source);
        assertTrue(source.contains("原题没有提供 Java 模板"), source);
    }
}
