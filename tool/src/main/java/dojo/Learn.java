package dojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** dojo learn：把 resources/patterns.md 里的条目原样打印出来，不经过模型。 */
public final class Learn {

    private Learn() {
    }

    public static int run(Repo repo, String[] args) {
        List<Patterns.Pattern> all = Patterns.load(repo);
        if (all.isEmpty()) {
            throw new DojoException("resources/patterns.md 不见了或没有条目；先 `git checkout resources/`。");
        }
        String id = null;
        boolean check = false;
        for (String arg : args) {
            if (arg.equals("--check")) {
                check = true;
            } else if (arg.equals("-h") || arg.equals("--help")) {
                usage();
                return 0;
            } else if (arg.startsWith("-")) {
                throw new DojoException("learn 不认识的选项：" + arg);
            } else {
                id = arg;
            }
        }
        if (check) {
            return check(repo, all);
        }
        if (id == null) {
            list(all);
            return 0;
        }
        Optional<Patterns.Pattern> found = Patterns.find(repo, id);
        if (found.isEmpty()) {
            Ui.err("没有这个模式：" + id);
            List<String> near = new ArrayList<>();
            for (Patterns.Pattern pattern : all) {
                if (pattern.id().contains(id) || pattern.name().contains(id)) {
                    near.add(pattern.id());
                }
            }
            if (!near.isEmpty()) {
                Ui.info("相近的：" + String.join("、", near));
            }
            Ui.info("dojo learn 看全部 " + all.size() + " 个。");
            return 1;
        }
        System.out.println(Patterns.render(repo, found.get()));
        return 0;
    }

    private static void usage() {
        Ui.title("dojo learn");
        System.out.println("  dojo learn                  列出全部模式");
        System.out.println("  dojo learn monotonic-stack  看一个模式的完整条目");
        System.out.println("  dojo learn --check          开发用：检查条目字段与外链（会联网）");
    }

    private static void list(List<Patterns.Pattern> all) {
        Ui.title(all.size() + " 个模式");
        int width = all.stream().mapToInt(p -> p.id().length()).max().orElse(12) + 2;
        for (Patterns.Pattern pattern : all) {
            String id = pattern.id();
            StringBuilder pad = new StringBuilder(id);
            while (pad.length() < width) {
                pad.append(' ');
            }
            String name = pattern.name();
            StringBuilder namePad = new StringBuilder(name);
            while (namePad.length() < 8) {
                namePad.append(' ');
            }
            System.out.println("  " + pad + namePad + "  " + pattern.essence());
        }
        System.out.println();
        Ui.info("dojo learn <id> 看识别信号、模板、练手题。");
    }

    private static int check(Repo repo, List<Patterns.Pattern> all) {
        int problems = 0;
        List<String> ids = new ArrayList<>();
        for (Patterns.Pattern pattern : all) {
            ids.add(pattern.id());
            problems += validate(repo, pattern);
        }
        for (String id : Patterns.IDS) {
            if (!ids.contains(id)) {
                Ui.warn("枚举里有、表里没有：" + id + "（模型给出这个 id 时不会显示尾巴）");
                problems++;
            }
        }
        if (problems == 0) {
            Ui.ok("字段检查通过：" + all.size() + " 个条目。");
        }
        int links = 0;
        int broken = 0;
        for (Patterns.Pattern pattern : all) {
            for (String url : new String[]{pattern.link(), pattern.backup()}) {
                if (url.isEmpty()) {
                    continue;
                }
                links++;
                if (!reachable(url)) {
                    Ui.warn("链接打不开：" + url + "（" + pattern.id() + "）");
                    broken++;
                }
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        Ui.info("外链 " + links + " 条，打不开 " + broken + " 条。");
        return problems + broken == 0 ? 0 : 1;
    }

    private static int validate(Repo repo, Patterns.Pattern pattern) {
        int problems = 0;
        if (!Patterns.IDS.contains(pattern.id())) {
            Ui.warn("条目 id 不在枚举里：" + pattern.id());
            problems++;
        }
        if (pattern.essence().isBlank()) {
            Ui.warn(pattern.id() + " 缺「本质」");
            problems++;
        }
        if (pattern.signals().size() < 2) {
            Ui.warn(pattern.id() + " 的「信号」少于 2 条");
            problems++;
        }
        if (pattern.before().isEmpty()) {
            Ui.warn(pattern.id() + " 缺「前置题」");
            problems++;
        }
        if (pattern.practice().isEmpty()) {
            Ui.warn(pattern.id() + " 缺「巩固题」");
            problems++;
        }
        if (pattern.pitfalls().size() < 2) {
            Ui.warn(pattern.id() + " 的「易错」少于 2 条");
            problems++;
        }
        if (!pattern.snippet().isEmpty()) {
            Path snippet = repo.root().resolve(pattern.snippet());
            if (!Files.isRegularFile(snippet)) {
                Ui.warn(pattern.id() + " 的模板文件不存在：" + pattern.snippet());
                problems++;
            }
        }
        return problems;
    }

    private static boolean reachable(String url) {
        try {
            Http.Response response = Http.get(url,
                    Map.of("user-agent", "Mozilla/5.0 dojo-learn-check"), Duration.ofSeconds(20));
            return response.status() >= 200 && response.status() < 400;
        } catch (DojoException e) {
            return false;
        }
    }
}
