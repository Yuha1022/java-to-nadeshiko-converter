package converter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class TestConverter {

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarator variable, Void arg) {
                int line = variable.getBegin().map(p -> p.line).orElse(-1);
                String converted = convertVariableDeclarator(variable);
                if (converted != null) {
                    items.add(new Item(line, converted));
                }
                super.visit(variable, arg);
            }
        }, null);

        return items;
    }

    private static String convertVariableDeclarator(VariableDeclarator variable) {
        String variableName = variable.getNameAsString();
        Expression initializer = variable.getInitializer().orElse(null);
        if (initializer == null) return null;

        // --- new Xxx(...) の場合 ---
        if (initializer.isObjectCreationExpr()) {
            ObjectCreationExpr objCreation = initializer.asObjectCreationExpr();

            // クラス名取得（ジェネリクス除去）
            String className;
            if (objCreation.getType().isClassOrInterfaceType()) {
                className = objCreation.getType()
                                       .asClassOrInterfaceType()
                                       .getName()
                                       .getIdentifier(); // ArrayList, FileWriterなど
            } else {
                String rawType = objCreation.getType().toString();
                className = rawType.replaceAll("<.*>", "");
            }

            // 引数部分を取得
            String args = "";
            if (!objCreation.getArguments().isEmpty()) {
                args = objCreation.getArguments().stream()
                                  .map(Expression::toString)
                                  .collect(Collectors.joining(", ", "(", ")"));
            }

            // 「points は ArrayList("data.txt")生成。」の形式で出力
            return variableName + " は " + className + args + "生成。";
        }

        // その他は変換しない
        return null;
    }

    // 出力用クラス
    public static class Item {
        public final int line;
        public final String content;

        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
    }
}
