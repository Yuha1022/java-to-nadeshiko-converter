package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * 初期化式のない変数宣言を処理するコンバーター
 * - ローカル変数: Hero h; → hはHero型。
 * - フィールド: String name; → nameとは文字列。
 */
public class FieldConverter {
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarationExpr variableDecl, Void arg) {
                // foreach文の変数宣言かどうかをチェック
                if (isForEachVariable(variableDecl)) {
                    // foreach文の変数宣言はスキップ
                    super.visit(variableDecl, arg);
                    return;
                }
                
                // ローカル変数の宣言を処理
                for (VariableDeclarator variable : variableDecl.getVariables()) {
                    // 初期化式がない場合のみ処理
                    if (!variable.getInitializer().isPresent()) {
                        int line = variable.getBegin().map(p -> p.line).orElse(-1);
                        String variableName = variable.getNameAsString();
                        Type type = variable.getType();
                        String typeName = convertTypeName(type.asString());
                        
                        if (typeName != null) {
                            String indent = IndentManager.getIndentForLine(line);
                            // ローカル変数の出力形式: 変数名は型名型。
                            items.add(new Item(line, indent + variableName + "とは" + typeName + "型。"));
                        }
                    }
                }
                super.visit(variableDecl, arg);
            }
            
            @Override
            public void visit(FieldDeclaration field, Void arg) {
                // フィールド（クラスのメンバー変数）の宣言を処理
                for (VariableDeclarator variable : field.getVariables()) {
                    // 初期化式がない場合のみ処理
                    if (!variable.getInitializer().isPresent()) {
                        int line = variable.getBegin().map(p -> p.line).orElse(-1);
                        String variableName = variable.getNameAsString();
                        Type type = variable.getType();
                        String typeName = convertTypeName(type.asString());
                        
                        if (typeName != null) {
                            // ★★★ここが重要：インデントを取得★★★
                            String indent = IndentManager.getIndentForLine(line);
                            // フィールドの出力形式: 変数名とは型名。
                            items.add(new Item(line, indent + variableName + "とは" + typeName + "。"));
                        }
                    }
                }
                super.visit(field, arg);
            }
        }, null);
        
        return items;
    }
    
    /**
     * foreach文の変数宣言かどうかを判定
     */
    private static boolean isForEachVariable(VariableDeclarationExpr variableDecl) {
        // 親ノードを辿ってForEachStmtかどうかを確認
        if (variableDecl.getParentNode().isPresent()) {
            Node parent = variableDecl.getParentNode().get();
            if (parent instanceof ForEachStmt) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Javaの型名を日本語に変換
     * int → 整数, String → 文字列, Hero → Hero など
     */
    private static String convertTypeName(String javaType) {
        // 配列型の場合
        if (javaType.endsWith("[]")) {
            String baseType = javaType.substring(0, javaType.length() - 2);
            String convertedBase = convertTypeName(baseType);
            if (convertedBase != null) {
                return convertedBase + "配列";
            }
            return baseType + "配列";
        }
        
        // 基本型の変換
        switch (javaType) {
            case "int":
            case "Integer":
            case "long":
            case "Long":
            case "short":
            case "Short":
            case "byte":
            case "Byte":
                return "整数";
            case "double":
            case "Double":
            case "float":
            case "Float":
                return "小数";
            case "boolean":
            case "Boolean":
                return "真偽値";
            case "char":
            case "Character":
                return "文字";
            case "String":
                return "文字列";
            default:
                // その他のクラス型はそのまま返す（例: Hero → Hero）
                return javaType;
        }
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