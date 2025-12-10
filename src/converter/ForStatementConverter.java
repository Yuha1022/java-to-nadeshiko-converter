package converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class ForStatementConverter { // for文をなでしこ形式に変換するクラス

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ForStmt forStmt, Void arg) {
                handleTraditionalForLoop(forStmt, items); // 通常のfor文の処理
                super.visit(forStmt, arg);
            }
            
            @Override
            public void visit(ForEachStmt foreachStmt, Void arg) {
                handleForEachLoop(foreachStmt, items); // foreach文（拡張for文）の処理
                super.visit(foreachStmt, arg);
            }
        }, null);
        return items;
    }

    private static void handleForEachLoop(ForEachStmt forEachStmt, List<Item> items) {
        int line = forEachStmt.getBegin().map(p -> p.line).orElse(-1);
        String outerIndent = IndentManager.getIndentForLine(line);

        // 変数名とコレクション名を取得
        String varName = forEachStmt.getVariable().getVariables().get(0).getNameAsString();
        String collection = ExpressionConverter.convertExpression(forEachStmt.getIterable());
        if (collection == null) {
            collection = forEachStmt.getIterable().toString();
        }

        // なでしこ形式のテキスト生成
        String forText = String.format("%s%sの各要素を%sへ取り出して繰り返す", outerIndent, collection, varName);
        items.add(new Item(line, forText));

        Statement bodyStmt = forEachStmt.getBody();
        // 終了処理
        addEndItem(bodyStmt, line, outerIndent, items);
    }

    private static void handleTraditionalForLoop(ForStmt forStmt, List<Item> items) {
        String initVar = "";
        String initVal = "";

        // 初期化式の処理 (例: i = 0)
        if (!forStmt.getInitialization().isEmpty()) {
            Expression initExpr = forStmt.getInitialization().get(0);
            if (initExpr.isVariableDeclarationExpr()) {
                VariableDeclarator vd = initExpr.asVariableDeclarationExpr().getVariable(0);
                initVar = vd.getNameAsString();
                initVal = vd.getInitializer().map(Object::toString).orElse("");
            } else if (initExpr.isAssignExpr()) {
                AssignExpr ae = initExpr.asAssignExpr();
                initVar = ae.getTarget().toString();
                initVal = ae.getValue().toString();
            }
        }
        String initPart = String.format("%sを%s", initVar, initVal);

        // 条件式の処理
        String compareText = forStmt.getCompare().map(ConditionConverter::convertCondition).orElse("");

        // 更新式の処理
        String update = forStmt.getUpdate().stream()
                .map(ExpressionConverter::convertExpression)
                .collect(Collectors.joining(", "));

        // for文の出力
        int line = forStmt.getBegin().map(p -> p.line).orElse(-1);
        String outerIndent = IndentManager.getIndentForLine(line);
        String forText = String.format("%s%sから(%s)まで%sを繰り返す", outerIndent, initPart, compareText, update);
        items.add(new Item(line, forText));

        Statement bodyStmt = forStmt.getBody();
        // 終了処理
        addEndItem(bodyStmt, line, outerIndent, items);
    }

    private static void addEndItem(Statement bodyStmt, int startLine, String indent, List<Item> items) {
        int endLine;
        if (bodyStmt instanceof BlockStmt) {
            Optional<Statement> lastStmt = ((BlockStmt) bodyStmt).getStatements().getLast();
            if (lastStmt.isPresent()) {
                endLine = lastStmt.get().getEnd().map(p -> p.line + 1).orElse(startLine + 1);
            } else {
                endLine = startLine + 1;
            }
        } else {
            endLine = bodyStmt.getEnd().map(p -> p.line + 1).orElse(startLine + 1);
        }
        items.add(new Item(endLine, indent + "ここまで。"));
    }

    public static class Item {
        public final int line;
        public final String content;

        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
    }
}