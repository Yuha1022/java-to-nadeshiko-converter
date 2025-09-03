package converter;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class PrintlnConverter {
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                if (methodCall.getScope().isPresent() &&
                    methodCall.getScope().get().toString().equals("System.out") &&
                    methodCall.getNameAsString().equals("println") &&
                    methodCall.getArguments().size() == 1) { // System.out.println(引数1つ)のとき
                    
                    int line = methodCall.getBegin().map(p -> p.line).orElse(-1); // 行番号を取得
                    String text = null;
                    
                    // 引数の型ごとに変換
                    if (methodCall.getArgument(0) instanceof StringLiteralExpr) { // 文字列
                        text = "「" + ((StringLiteralExpr) methodCall.getArgument(0)).asString() + "」と表示。";
                    } else if (methodCall.getArgument(0) instanceof IntegerLiteralExpr) { // 整数
                        text = ((IntegerLiteralExpr) methodCall.getArgument(0)).getValue() + "と表示。";
                    } else if (methodCall.getArgument(0) instanceof DoubleLiteralExpr) { // 浮動小数点
                        text = ((DoubleLiteralExpr) methodCall.getArgument(0)).asDouble() + "と表示。";
                    } else if (methodCall.getArgument(0) instanceof UnaryExpr) {
                        UnaryExpr unary = (UnaryExpr) methodCall.getArgument(0);
                        if (unary.getOperator() == UnaryExpr.Operator.MINUS) { // マイナス付きの数値のとき
                            if (unary.getExpression() instanceof IntegerLiteralExpr) { //整数
                                text = "-" + ((IntegerLiteralExpr) unary.getExpression()).getValue() + "と表示。";
                            } else if (unary.getExpression() instanceof DoubleLiteralExpr) { // 浮動小数点
                                text = "-" + ((DoubleLiteralExpr) unary.getExpression()).asDouble() + "と表示。";
                            }
                        }
                    }
                    
                    if (text != null) { // リストに追加
                        items.add(new Item(line, text));
                    }
                }
                super.visit(methodCall, arg); // 子要素も探索する
            }
        }, null);
        return items;
    }

    public static class Item { // 行番号と内容を保持するクラス
        public final int line;
        public final String content;
        
        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
    }
}