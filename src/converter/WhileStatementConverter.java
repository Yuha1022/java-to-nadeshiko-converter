package converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class WhileStatementConverter {

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(WhileStmt whileStmt, Void arg) {
                int line = whileStmt.getBegin().map(p -> p.line).orElse(-1);
                Expression condition = whileStmt.getCondition();
                String conditionText = ConditionConverter.convertCondition(condition);
                
                // 親をたどってインデントレベルを計算する
                String whileIndent = IndentManager.getIndentForLine(line);
                
                String suffix;
                if (condition instanceof com.github.javaparser.ast.expr.MethodCallExpr) {
                    suffix = "間";
                } else {
                    suffix = "の間";
                }
                String whileText = whileIndent + "(" + conditionText + ")" + suffix;
                items.add(new Item(line, whileText));
                
                // while文本体のインデントを記録
                Statement bodyStmt = whileStmt.getBody();
                
                // ブロックの最後の文の次の行に「ここまで。」を配置する
                int endLine;
                if (bodyStmt instanceof BlockStmt) {
                    Optional<Statement> lastStmt = ((BlockStmt) bodyStmt).getStatements().getLast();
                    if (lastStmt.isPresent()) {
                        endLine = lastStmt.get().getEnd().map(p -> p.line + 1).orElse(line + 1);
                    } else {
                        endLine = line + 1;
                    }
                } else {
                    endLine = bodyStmt.getEnd().map(p -> p.line + 1).orElse(line + 1);
                }
                items.add(new Item(endLine, whileIndent + "ここまで。"));
                
                super.visit(whileStmt, arg);
            }
        }, null);
        
        return items;
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