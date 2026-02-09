package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class TryCatchConverter {

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(TryStmt tryStmt, Void arg) {
                int line = tryStmt.getBegin().map(p -> p.line).orElse(-1);
                String outerIndent = IndentManager.getIndentForLine(line);

                // try 節開始
                items.add(new Item(tryStmt.getTryBlock().getBegin().map(p -> p.line).orElse(line), outerIndent + "エラー監視"));

                // try-with-resources のリソース処理
                if (tryStmt.getResources() != null && !tryStmt.getResources().isEmpty()) {
                    String resourceIndent = outerIndent + "　";
                    for (Expression resource : tryStmt.getResources()) {
                        int resourceLine = resource.getBegin().map(p -> p.line).orElse(line);
                        String resourceText = VariableInitConverter.convertInitializer(
                                resource.asVariableDeclarationExpr().getVariable(0).getNameAsString(),
                                resource.asVariableDeclarationExpr().getVariable(0).getInitializer().get(), false);
                        items.add(new Item(resourceLine, resourceIndent + resourceText));
                    }
                }

                // catch 節
                for (CatchClause cc : tryStmt.getCatchClauses()) {
                    int catchLine = cc.getBegin().map(p -> p.line).orElse(-1);
                    Parameter param = cc.getParameter();
                    String exceptionType = param.getType().asString();
                    String exceptionVar = param.getNameAsString();
                    items.add(new Item(catchLine, outerIndent + convertCatchClause(exceptionType, exceptionVar)));
                }

                // finally 節（ここがポイント）
                if (tryStmt.getFinallyBlock().isPresent()) {
                    BlockStmt finallyBlock = tryStmt.getFinallyBlock().get();
                    int finallyLine = finallyBlock.getBegin().map(p -> p.line).orElse(-1); // finallyキーワードの行
                    items.add(new Item(finallyLine, outerIndent + "後処理"));
                }

                // 終了
                int endLine = tryStmt.getEnd().map(p -> p.line).orElse(-1);
                items.add(new Item(endLine, outerIndent + "ここまで。"));

                super.visit(tryStmt, arg);
            }

        }, null);

        return items;
    }

    private static String convertCatchClause(String exceptionType, String varName) {
        String errorTypeName;
        switch (exceptionType) {
            case "IOException":
                errorTypeName = "ファイルエラー";
                break;
            case "IllegalArgumentException":
                errorTypeName = "不正な引数エラー";
                break;
            case "Exception":
                errorTypeName = "基本エラー";
                break;
            default:
                errorTypeName = exceptionType; // 不明なエラーはそのまま表示
        }
        return "エラー " + varName + " が " + errorTypeName + " ならば";
    }

    /**
     * MethodConverterから呼び出され、try-catch-finallyの各ブロックのインデントを記録する
     */
    public static void recordBlockIndents(TryStmt tryStmt, String parentIndent) {
        String bodyIndent = parentIndent + "　";
        // try 本体
        MethodConverter.MethodVisitor.processBlock(tryStmt.getTryBlock(), bodyIndent);

        // catch 節
        tryStmt.getCatchClauses().forEach(cc -> MethodConverter.MethodVisitor.processBlock(cc.getBody(), bodyIndent));

        // finally 節
        tryStmt.getFinallyBlock().ifPresent(fb -> MethodConverter.MethodVisitor.processBlock(fb, bodyIndent));
    }

    // 出力構造体
    public static class Item {
        public final int line;
        public final String content;

        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
    }
}
