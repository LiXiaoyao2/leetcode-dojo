class Solution {
    public String longestPalindrome(String s) {
        String best = "";
        for (int i = 0; i < s.length(); i++) {
            String one = expand(s, i, i);
            if (one.length() > best.length()) {
                best = one;
            }
        }
        return best;
    }

    private String expand(String s, int left, int right) {
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            left--;
            right++;
        }
        return s.substring(left + 1, right);
    }
}
