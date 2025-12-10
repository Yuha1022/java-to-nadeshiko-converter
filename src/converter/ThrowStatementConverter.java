package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * throw文をなでしこ形式に変換するクラス
 * throw new Exception("メッセージ") → 「メッセージ」とエラー発生。
 */
public class ThrowStatementConverter {
    
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ThrowStmt stmt, Void arg) {
                int line = stmt.getBegin().map(p -> p.line).orElse(-1);
                Expression throwExpr = stmt.getExpression();
                
                String errorMessage = "";
                
                // 例外オブジェクトの生成式から引数を取得
                if (throwExpr instanceof ObjectCreationExpr) {
                    ObjectCreationExpr objCreation = (ObjectCreationExpr) throwExpr;
                    if (!objCreation.getArguments().isEmpty()) {
                        // ExpressionConverterを使って文字列連結を含む式を変換する
                        Expression argument = objCreation.getArguments().get(0);
                        errorMessage = ExpressionConverter.convertExpression(argument);
                    }
                }
                
                // インデントを取得
                String indent = IndentManager.getIndentForLine(line);
                
                // なでしこ形式で出力
                if (errorMessage == null || errorMessage.isEmpty()) {
                    items.add(new Item(line, indent + "エラー発生。"));
                } else {
                    items.add(new Item(line, indent + errorMessage + "とエラー発生。"));
                }
                
                super.visit(stmt, arg);
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