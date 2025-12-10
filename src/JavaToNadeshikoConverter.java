import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import converter.ArrayConverter;
import converter.ClassConverter;
import converter.CommentConverter;
import converter.ConditionConverter;
import converter.ExpressionConverter;
import converter.FieldConverter;
import converter.ForStatementConverter;
import converter.IfStatementConverter;
import converter.ImportConverter;
import converter.IndentManager;
import converter.MethodConverter;
import converter.PackageConverter;
import converter.PrintlnConverter;
import converter.SwitchStatementConverter;
import converter.ThrowStatementConverter;
import converter.TryCatchConverter;
import converter.VariableInitConverter;
import converter.WhileStatementConverter;

public class JavaToNadeshikoConverter {
    public static void main(String[] args) throws Exception {
        // コンパイル時に間接的に参照されるクラスを確実に含めるためのダミー参照
        try {
            Class.forName(ExpressionConverter.class.getName());
            Class.forName(ConditionConverter.class.getName());
            Class.forName(IndentManager.class.getName());
        } catch (ClassNotFoundException e) {
        }

        if (args.length == 0) {
            System.out.println("Javaファイル名を指定してください");
            return;
        }

        String javaCode = new String(Files.readAllBytes(Paths.get(args[0])));

        ParserConfiguration config = new ParserConfiguration();
        // Java 16以上に設定（instanceof パターンマッチング対応）
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_16);
        JavaParser parser = new JavaParser(config);
        ParseResult<CompilationUnit> result = parser.parse(javaCode);
        if (!result.isSuccessful()) {
            System.err.println("構文木作成に失敗しました");
            result.getProblems().forEach(problem -> System.err.println(problem.getMessage()));
            return;
        }
        CompilationUnit cu = result.getResult().get();

        // インデントマネージャーを初期化
        IndentManager.clear();

        // --- 変換処理 ---
        // 1. 最初にクラスとメソッドを処理し、インデントの骨格を作る
        List<ClassConverter.Item> classes = ClassConverter.convert(cu);
        List<MethodConverter.Item> methods = MethodConverter.convert(cu);

        // 2. その他の要素を変換
        //    コメントはインデント情報が記録された後に処理する必要があるため、MethodConverterの後に呼び出す
        List<ForStatementConverter.Item> forStatements = ForStatementConverter.convert(cu);
        List<WhileStatementConverter.Item> whileStatements = WhileStatementConverter.convert(cu);
        List<IfStatementConverter.Item> ifStatements = IfStatementConverter.convert(cu);
        List<SwitchStatementConverter.Item> switchStatements = SwitchStatementConverter.convert(cu);
        List<TryCatchConverter.Item> trys = TryCatchConverter.convert(cu);
        List<FieldConverter.Item> fields = FieldConverter.convert(cu);
        List<ArrayConverter.Item> arrays = ArrayConverter.convert(cu);
        List<CommentConverter.Item> comments = CommentConverter.convert(cu);
        List<VariableInitConverter.Item> variables = VariableInitConverter.convert(cu);
        List<PrintlnConverter.Item> prints = PrintlnConverter.convert(cu);
        List<PackageConverter.Item> packages = PackageConverter.convert(cu);
        List<ImportConverter.Item> imports = ImportConverter.convert(cu);
        List<ThrowStatementConverter.Item> throwes = ThrowStatementConverter.convert(cu);

        List<Item> all = new ArrayList<>();
        for (ForStatementConverter.Item f : forStatements)
            all.add(new Item(f.line, f.content, 50));
        for (WhileStatementConverter.Item w : whileStatements)
            all.add(new Item(w.line, w.content, 50));
        for (IfStatementConverter.Item i : ifStatements)
            all.add(new Item(i.line, i.content, i.priority));
        for (SwitchStatementConverter.Item i : switchStatements)
            all.add(new Item(i.line, i.content, 50));
        for (FieldConverter.Item p : fields)
            all.add(new Item(p.line, p.content, 50));
        for (ArrayConverter.Item a : arrays) // 優先度をコメントより低く設定
            all.add(new Item(a.line, a.content, 50));
        for (CommentConverter.Item c : comments)
            all.add(new Item(c.line, c.content, c.priority)); // コメントの優先度を高く設定
        for (VariableInitConverter.Item v : variables)
            all.add(new Item(v.line, v.content, 50));
        for (MethodConverter.Item m : methods)
            all.add(new Item(m.line, m.content, m.priority));
        for (PrintlnConverter.Item p : prints)
            all.add(new Item(p.line, p.content, 50));
        for (PackageConverter.Item p : packages)
            all.add(new Item(p.line, p.content, 50));
        for (ImportConverter.Item p : imports)
            all.add(new Item(p.line, p.content, 50));
        for (ClassConverter.Item p : classes)
            all.add(new Item(p.line, p.content, 50));
        for (ThrowStatementConverter.Item p : throwes)
            all.add(new Item(p.line, p.content, 50));
        for (TryCatchConverter.Item p : trys)
            all.add(new Item(p.line, p.content, 50));
        // for (TestConverter.Item p : tests)
        // all.add(new Item(p.line, p.content, 50));

        // 行番号ごとに変換結果をグループ化
        Map<Integer, List<Item>> itemsByLine = new TreeMap<>();
        for (Item item : all) {
            itemsByLine.computeIfAbsent(item.line, k -> new ArrayList<>()).add(item);
        }

        // 元のコードを行に分割
        String[] sourceLines = javaCode.split("\r\n|\r|\n", -1);

        // 1行目から最終行までループ
        for (int i = 1; i <= sourceLines.length; i++) {
            if (itemsByLine.containsKey(i)) {
                // この行に変換されたコンテンツがある場合
                List<Item> lineItems = itemsByLine.get(i);
                lineItems.sort(Comparator.comparingInt(item -> item.priority));
                for (Item item : lineItems) {
                    System.out.println(item.content);
                }
            } else if (sourceLines[i - 1].trim().isEmpty()) {
                // 変換されたコンテンツがなく、元の行が空行の場合
                System.out.println();
            }
        }
    }

    static class Item {
        int line;
        String content;
        int priority;

        Item(int line, String content, int priority) {
            this.line = line;
            this.content = content;
            this.priority = priority;
        }
    }
}