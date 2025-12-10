package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class PrintlnConverter { // System.out.println文をなでしこ形式に変換するクラス

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>(); // 変換結果を格納するリスト
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) { // メソッド呼び出しの場合
                if (isPrintlnCall(methodCall)) { // System.out.printlnの場合
                    int line = methodCall.getBegin().map(p -> p.line).orElse(-1); // 行番号取得
                    String indent = IndentManager.getIndentForLine(line); // 行のインデントを取得
                    if (!methodCall.getArguments().isEmpty()) { // 引数が存在する場合
                        String content = convertPrintContent(methodCall.getArguments().get(0)); // 最初の引数を取得
                        String text = indent + content + "と表示。"; // なでしこ形式のテキスト生成
                        items.add(new Item(line, text)); // 変換結果をリストに追加
                    } else { // 引数がない場合
                        String text = indent + "改行。";
                        items.add(new Item(line, text)); // 変換結果をリストに追加
                    }
                } else if (isPrintCall(methodCall)) { // System.out.printの場合
                    int line = methodCall.getBegin().map(p -> p.line).orElse(-1); // 行番号取得
                    if (!methodCall.getArguments().isEmpty()) { // 引数が存在する場合
                        String content = convertPrintContent(methodCall.getArguments().get(0)); // 最初の引数を取得
                        String indent = IndentManager.getIndentForLine(line); // 行のインデントを取得
                        String text = indent + content + "と無改行表示。"; // なでしこ形式のテキスト生成
                        items.add(new Item(line, text)); // 変換結果をリストに追加
                    }
                }
                super.visit(methodCall, arg); // 子ノードの訪問
            }
        }, null);

        return items;
    }

    private static boolean isPrintlnCall(MethodCallExpr methodCall) { // System.out.printlnかどうか判定
        String methodName = methodCall.getNameAsString();
        // println または pritnln (typo) を検出
        if (!"println".equals(methodName) && !"pritnln".equals(methodName)) {
            return false;
        }
        if (methodCall.getScope().isPresent()) { // スコープ(System.out)が存在する場合
            Expression scope = methodCall.getScope().get(); // スコープ取得
            if (scope instanceof FieldAccessExpr) { // フィールドアクセスの場合
                FieldAccessExpr fieldAccess = (FieldAccessExpr) scope; // フィールドアクセス取得
                if ("out".equals(fieldAccess.getNameAsString())) { // フィールド名がoutの場合
                    if (fieldAccess.getScope() instanceof NameExpr) { // スコープが名前式の場合
                        NameExpr nameExpr = (NameExpr) fieldAccess.getScope(); // 名前式取得
                        return "System".equals(nameExpr.getNameAsString()); // 名前式がSystemの場合
                    }
                }
            }
        }
        return false;
    }

    private static boolean isPrintCall(MethodCallExpr methodCall) { // System.out.printかどうか判定
        if (!"print".equals(methodCall.getNameAsString())) { // メソッド名がprintでない場合
            return false;
        }
        if (methodCall.getScope().isPresent()) { // スコープ(System.out)が存在する場合
            Expression scope = methodCall.getScope().get(); // スコープ取得
            if (scope instanceof FieldAccessExpr) { // フィールドアクセスの場合
                FieldAccessExpr fieldAccess = (FieldAccessExpr) scope; // フィールドアクセス取得
                if ("out".equals(fieldAccess.getNameAsString())) { // フィールド名がoutの場合
                    if (fieldAccess.getScope() instanceof NameExpr) { // スコープが名前式の場合
                        NameExpr nameExpr = (NameExpr) fieldAccess.getScope(); // 名前式取得
                        return "System".equals(nameExpr.getNameAsString()); // 名前式がSystemの場合
                    }
                }
            }
        }
        return false;
    }

    private static String convertPrintContent(Expression expr) { // なでしこ形式のテキスト生成
        String converted = ExpressionConverter.convertExpression(expr); // 複雑な式を変換
        if (converted != null) { // 変換できた場合
            return converted;
        }
        return "(" + ExpressionConverter.convertExpression(expr) + ")";
    }

    public static class Item { // 行番号と内容をまとめたクラス
        public final int line;
        public final String content;

        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
    }
}