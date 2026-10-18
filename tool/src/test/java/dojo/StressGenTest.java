package dojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressGenTest {

    private static final String TWO_SUM = """
            package p0001_two_sum;

            class Solution {
                public int[] twoSum(int[] nums, int target) {
                    throw new UnsupportedOperationException("TODO: 还没写");
                }
            }
            """;

    @Test
    void readsTheSignatureFromTheFileOnDisk() {
        StressGen.Spec spec = StressGen.parse(TWO_SUM);
        assertEquals("twoSum", spec.method());
        assertEquals("int[]", spec.returnType());
        assertEquals(2, spec.params().size());
        assertEquals("nums", spec.params().get(0).name());
        assertEquals("int[]", spec.params().get(0).type());
        assertEquals("target", spec.params().get(1).name());
        assertEquals("int", spec.params().get(1).type());
    }

    @Test
    void targetUsesTwoDistinctIndicesAndAnEmptyRunFailsLoudly() {
        String source = StressGen.testSource("p0001_two_sum", "TwoSumStressTest", StressGen.parse(TWO_SUM), 300, 0);
        assertTrue(source.contains("target = nums.length >= 2 ? targetOf(rnd, nums)"), source);
        assertTrue(source.contains("int i = rnd.nextInt(a.length);"), source);
        assertTrue(source.contains("int j = (i + 1 + rnd.nextInt(a.length - 1)) % a.length;"), source);
        assertTrue(source.contains("int tested = 0;"), source);
        assertTrue(source.contains("tested++;"), source);
        assertTrue(source.contains("if (tested == 0)"), source);
        assertTrue(source.contains("brute.twoSum(nums, target)"), source);
        assertTrue(source.contains("solution.twoSum(nums, target)"), source);
        // two-sum 允许任意下标顺序：对拍不能把 [1,0] 当成错
        assertTrue(source.contains("int[] e = expected.clone();"), source);
        assertTrue(source.contains("Arrays.sort(e);"), source);
        assertTrue(source.contains("Arrays.equals(e, a)"), source);
        assertTrue(source.contains("seed=\" + seed"), source);
    }

    @Test
    void linkedListGetsBuildersAndArrayComparison() {
        String src = """
                class Solution {
                    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
                        return null;
                    }
                }
                """;
        StressGen.Spec spec = StressGen.parse(src);
        String source = StressGen.testSource("p0002_add_two_numbers", "AddTwoNumbersStressTest", spec, 10, 7);
        assertTrue(source.contains("import kit.ListNodes;"), source);
        assertTrue(source.contains("ListNodes.of(randInts(rnd, 0, 6))"), source);
        assertTrue(source.contains("ListNodes.toArray(expected)"), source);
    }

    @Test
    void treeDeclarationsImportTheTypeNotJustTheBuilder() {
        String src = """
                class Solution {
                    public boolean isValidBST(TreeNode root) {
                        return false;
                    }
                }
                """;
        String source = StressGen.testSource("p0098_validate_binary_search_tree", "ValidateBinarySearchTreeStressTest",
                StressGen.parse(src), 10, 0);
        assertTrue(source.contains("import kit.TreeNode;"), source);
        assertTrue(source.contains("import kit.TreeNodes;"), source);
        assertTrue(source.contains("TreeNode root = TreeNodes.of(randTree(rnd))"), source);
    }

    @Test
    void twoSumSkipsInputsWithMoreThanOneValidPair() {
        String source = StressGen.testSource("p0001_two_sum", "TwoSumStressTest", StressGen.parse(TWO_SUM), 10, 0);
        assertTrue(source.contains("if (pairs != 1) continue;"), source);
    }

    @Test
    void designProblemsAndGenericsAreRejectedWithAReason() {
        String design = "class LRUCache { public int get(int key) { return -1; } }";
        StressGen.Unsupported e = assertThrows(StressGen.Unsupported.class, () -> StressGen.parse(design));
        assertTrue(e.getMessage().contains("没在 Solution.java 里找到可对拍的方法"), e.getMessage());

        String generics = "class Solution { public List<List<Integer>> permute(int[] nums) { return null; } }";
        assertThrows(StressGen.Unsupported.class, () -> StressGen.parse(generics));
    }

    @Test
    void bruteValidationReusesTheOfficialSamples() {
        String official = "package p0001_two_sum;\n\nclass TwoSumTest {\n"
                + "    private final Solution solution = new Solution();\n}\n";
        String renamed = Stress.bruteTestSource(official, "TwoSumTest", "TwoSumBruteTest");
        assertTrue(renamed.contains("class TwoSumBruteTest {"), renamed);
        assertTrue(renamed.contains("private final Brute solution = new Brute();"), renamed);
        assertTrue(!renamed.contains("Solution"), renamed);
    }
}
