package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class ArrayConverter { //配列をなでしこ形式に変換するクラス
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>(); //変換結果を格納するリスト
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarator variable, Void arg) { //変数宣言の場合
                if (variable.getInitializer().isPresent()) { //初期化式の場合
                    Expression initializer = variable.getInitializer().get(); //初期化式取得
                    if (initializer instanceof ArrayCreationExpr) { //配列の生成式の場合(例: int[] arr = new int[5];)
                        ArrayCreationExpr arrayCreation = (ArrayCreationExpr) initializer; //配列生成式取得
                        int line = variable.getBegin().map(p -> p.line).orElse(-1); //行番号取得
                        String variableName = variable.getNameAsString(); //変数名取得
                        String arrayContent = convertArrayCreation(arrayCreation); //なでしこ形式に変換
                        if (arrayContent != null) { //変換できた場合
                            String indent = IndentManager.getIndentForLine(line); //行のインデントを取得
                            String text = variableName + "は" + arrayContent + "。"; //なでしこ形式のテキスト生成
                            items.add(new Item(line, indent + text)); //変換結果をリストに追加
                        }
                    } else if (initializer instanceof ArrayInitializerExpr) { //配列初期化式の場合(例:int[] arr = {1,2,3};）
                        ArrayInitializerExpr arrayInit = (ArrayInitializerExpr) initializer; //配列初期化式取得
                        int line = variable.getBegin().map(p -> p.line).orElse(-1); //行番号取得
                        String variableName = variable.getNameAsString(); //変数名取得
                        String arrayContent = convertArrayInitializer(arrayInit); //なでしこ形式に変換
                        if (arrayContent != null) { //変換できた場合
                            String indent = IndentManager.getIndentForLine(line); //行のインデントを取得
                            String text = variableName + "は" + arrayContent + "。"; //なでしこ形式のテキスト生成
                            items.add(new Item(line, indent + text)); //変換結果をリストに追加
                        }
                    }
                }
                super.visit(variable, arg);
            }
            
            @Override
            public void visit(com.github.javaparser.ast.expr.AssignExpr assignExpr, Void arg) { //代入式の場合(例: arr = {1,2,3};)
                if (assignExpr.getOperator() == com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN) { //=の場合
                    Expression value = assignExpr.getValue(); //代入部分(右辺)取得
                    if (value instanceof ArrayCreationExpr || value instanceof ArrayInitializerExpr) { //配列の場合
                        int line = assignExpr.getBegin().map(p -> p.line).orElse(-1); //行番号取得
                        String variableName = assignExpr.getTarget().toString(); //変数名取得
                        String arrayContent = null; //なでしこ形式に変換結果格納用
                        if (value instanceof ArrayCreationExpr) { //配列生成式の場合
                            arrayContent = convertArrayCreation((ArrayCreationExpr) value); //なでしこ形式に変換
                        } else if (value instanceof ArrayInitializerExpr) { //配列初期化式の場合
                            arrayContent = convertArrayInitializer((ArrayInitializerExpr) value); //なでしこ形式に変換
                        }
                        if (arrayContent != null) { //変換できた場合
                            String indent = IndentManager.getIndentForLine(line); //行のインデントを取得
                            String text = variableName + "は" + arrayContent + "。"; //なでしこ形式のテキスト生成
                            items.add(new Item(line, indent + text)); //変換結果をリストに追加
                        }
                    }
                }
                super.visit(assignExpr, arg);
            }

        }, null);
        return items;
    }

    private static String convertArrayCreation(ArrayCreationExpr arrayCreation) {
        // 配列の初期化子がある場合 (例: new int[] {1, 2, 3}) はそちらを優先
        if (arrayCreation.getInitializer().isPresent()) {
            return convertArrayInitializer(arrayCreation.getInitializer().get());
        }

        String elementType = arrayCreation.getElementType().asString();
        String nadeshikoType = convertJavaTypeToNadeshiko(elementType);
        var levels = arrayCreation.getLevels();

        if (levels.isEmpty() || levels.get(0).getDimension().isEmpty()) {
            return null; // サイズ指定のない配列生成は非対応
        }

        // 各次元のサイズを取得
        List<String> dimensions = new ArrayList<>();
        for (var level : levels) {
            level.getDimension().ifPresent(dim -> dimensions.add(dim.toString()));
        }

        if (dimensions.isEmpty()) {
            return null;
        }

        String sizeSpec;
        if (dimensions.size() == 1) {
            sizeSpec = String.format("長さ%s", dimensions.get(0));
        } else if (dimensions.size() == 2) {
            sizeSpec = String.format("行%s,列%s", dimensions.get(0), dimensions.get(1));
        } else {
            sizeSpec = String.join("×", dimensions);
        }

        return String.format("%s配列(%s)生成", nadeshikoType, sizeSpec);
    }

    private static String convertMultiDimensionalArray(ArrayCreationExpr arrayCreation) {
        String elementType = arrayCreation.getElementType().asString();
        String defaultValue = getDefaultValue(elementType);
        
        List<Integer> dimensions = new ArrayList<>();
        for (var level : arrayCreation.getLevels()) {
            if (level.getDimension().isPresent()) {
                Expression dim = level.getDimension().get();
                if (dim instanceof IntegerLiteralExpr) {
                    dimensions.add(Integer.parseInt(((IntegerLiteralExpr) dim).getValue()));
                }
            }
        }
        
        if (dimensions.isEmpty()) {
            return null;
        }
        
        return buildMultiDimensionalArray(dimensions, 0, defaultValue);
    }

    private static String buildMultiDimensionalArray(List<Integer> dimensions, int currentDim, String defaultValue) {
        if (currentDim >= dimensions.size()) {
            return defaultValue;
        }
        
        int size = dimensions.get(currentDim);
        StringBuilder sb = new StringBuilder("[");
        
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(",");
            
            if (currentDim == dimensions.size() - 1) {
                // 最後の次元の場合、デフォルト値を設定
                sb.append(defaultValue);
            } else {
                // 次の次元を再帰的に処理
                sb.append(buildMultiDimensionalArray(dimensions, currentDim + 1, defaultValue));
            }
        }
        
        sb.append("]");
        return sb.toString();
    }

    private static String convertArrayInitializer(ArrayInitializerExpr arrayInit) { //配列初期化式の処理
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arrayInit.getValues().size(); i++) {
            if (i > 0) sb.append(","); //要素の区切り
            Expression value = arrayInit.getValues().get(i); //要素取得
            
            // ネストされた配列初期化式の場合
            if (value instanceof ArrayInitializerExpr) {
                sb.append(convertArrayInitializer((ArrayInitializerExpr) value));
            } else {
                sb.append(value.toString()); //要素追加
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Javaの型名を日本語の型名に変換する
     * (FieldConverterからロジックを拝借)
     */
    private static String convertJavaTypeToNadeshiko(String javaType) {
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
                return javaType;
        }
    }

    private static String getDefaultValue(String type) { //型に応じたデフォルト値を取得
        switch (type) {
            case "int":
            case "long":
            case "short":
            case "byte":
                return "0";
            case "double":
            case "float":
                return "0.0";
            case "boolean":
                return "偽";
            case "char":
                return "''";
            default:
                return "null";
        }
    }
    
    public static String convertArrayCreationPublic(ArrayCreationExpr arrayCreation) { //配列生成式の処理
        return convertArrayCreation(arrayCreation);
    }

    public static String convertArrayInitializerPublic(ArrayInitializerExpr arrayInit) { //配列初期化式の処理
        return convertArrayInitializer(arrayInit);
    }

    public static class Item { //行番号と内容をまとめたクラス
        public final int line;
        public final String content;
        
        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
        
    }
}