import java.util.*;

class Solution {
    public int lengthOfLongestSubstring(String s) {
        Set<Character> seen = new HashSet<>();
        int best = 0;
        for (int i = 0; i < s.length(); i++) {
            if (!seen.add(s.charAt(i))) {
                best = Math.max(best, seen.size());
                seen.clear();
                seen.add(s.charAt(i));
            }
        }
        return Math.max(best, seen.size());
    }
}
