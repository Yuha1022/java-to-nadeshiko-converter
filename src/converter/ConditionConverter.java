package converter;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;

public class ConditionConverter { // Javaの条件式を日本語に変換するクラス

    public static String convertCondition(Expression condition) {
        return convertCondition(condition, true, true);
    }

    private static String convertCondition(Expression condition, boolean wrapInParentheses, boolean wrapInnerConditions) {
        // 括弧で囲まれた式は中身を再帰的に処理
        if (condition instanceof EnclosedExpr) {
            EnclosedExpr enclosed = (EnclosedExpr) condition;
            String inner = convertCondition(enclosed.getInner(), wrapInParentheses, wrapInnerConditions);
            if (wrapInParentheses && !inner.startsWith("(")) {
                return "(" + inner + ")";
            }
            return inner;
        }
        // 特殊ケース: (methodCall == true) のような式を優先して「methodCallが真」の形で出す
        if (condition instanceof BinaryExpr) {
            BinaryExpr be = (BinaryExpr) condition;
            // equals comparison to literal true: e.g. h1.equals(h2) == true
            if (be.getOperator() == BinaryExpr.Operator.EQUALS) {
                Expression left = be.getLeft();
                Expression right = be.getRight();
                // 左がメソッド呼び出しで右が true の場合
                if (left instanceof MethodCallExpr && right instanceof BooleanLiteralExpr) {
                    boolean val = ((BooleanLiteralExpr) right).getValue();
                    if (val) {
                        // メソッド呼び出しを日本語に変換してから「が真」を付ける
                        return convertCondition(left, false, false) + "が真";
                    }
                }
                // 右がメソッド呼び出しで左が true の場合
                if (right instanceof MethodCallExpr && left instanceof BooleanLiteralExpr) {
                    boolean val = ((BooleanLiteralExpr) left).getValue();
                    if (val) {
                        return convertCondition(right, false, false) + "が真";
                    }
                }
            }
        }

        // メソッド呼び出し (equals, equalsIgnoreCase, isEmpty, contains, endsWith 等) を条件として扱う
        if (condition instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) condition;
            String mname = mc.getNameAsString();

            if ("isEqual".equals(mname) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            String other = convertExpressionToString(mc.getArgument(0));
            return scope + "が" + other + "と等しい";
        }

            // equals / equalsIgnoreCase: "left と right が等しい"
            if (("equals".equals(mname) || "equalsIgnoreCase".equals(mname)) && mc.getArguments().size() == 1) {
                String left = mc.getScope().map(e -> convertExpressionToString(e)).orElse(mc.getScope().map(Object::toString).orElse(""));
                String right = convertExpressionToString(mc.getArgument(0));
                return left + "と" + right + "が等しい";
            }

            // isAfter / isBefore
            if (("isAfter".equals(mname) || "isBefore".equals(mname)) && mc.getArguments().size() == 1) {
                String left = mc.getScope().map(e -> convertExpressionToString(e)).orElse("");
                String right = convertExpressionToString(mc.getArgument(0));
                String comparison = "isAfter".equals(mname) ? "より未来" : "より過去";
                return left + "が" + right + comparison;
            }

            // it.hasNext() -> itの次の要素がある
            if ("hasNext".equals(mname) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse("");
                return scope + "の次の要素がある";
            }

            // prefs.containsKey(key) -> prefsのキーにkeyを含む
            if ("containsKey".equals(mname) && mc.getArguments().size() == 1) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse("");
                String arg = convertExpressionToString(mc.getArgument(0));
                return scope + "のキーに" + arg + "を含む";
            }

           // prefs.containsValue(value) -> prefsの値にvalueCheckを含む
            if ("containsValue".equals(mname) && mc.getArguments().size() == 1) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse("");
                String arg = convertExpressionToString(mc.getArgument(0));
                return scope + "の値に" + arg + "を含む";
            }

            // contains: "scope に 「arg」が含まれている"
            if ("contains".equals(mname) && mc.getArguments().size() == 1) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse("");
                String arg = convertExpressionToString(mc.getArgument(0));
                return scope + "が" + arg + "を含む";
            }

            // endsWith: "scope が 「arg」で終わる"
            if ("endsWith".equals(mname) && mc.getArguments().size() == 1) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse(mc.getScope().map(Object::toString).orElse(""));
                String arg = convertExpressionToString(mc.getArgument(0));
                return scope + "が" + arg + "で終わる";
            }

            // isEmpty(): "scope の長さが0"
            if ("isEmpty".equals(mname) && mc.getArguments().isEmpty()) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse("");
                return scope + "が空";
            }

            // 他のメソッド呼び出しは "〜が真" として扱う
            return convertExpressionToString(mc) + "が真";
        }

        // NOT演算子の処理
        if (condition instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) condition;
            if (unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                // !(expr) の形式を変換
                // 内部の条件を完全に日本語化するため、falseを渡して括弧を制御
                String innerCondition = convertCondition(unary.getExpression(), false, true);

                // 文字列長などの判定: 「〜の長さが0」→「〜の長さが0でない」
                if (innerCondition.endsWith("の長さが0")) {
                    return innerCondition + "でない";
                }

                // 内部条件が既に「〜が真」「〜が偽」の形式の場合
                if (innerCondition.endsWith("が真")) {
                    String base = innerCondition.substring(0, innerCondition.length() - 2);
                    // 余計な括弧は付けず、そのまま反転
                    return base + "でない";
                }
                if (innerCondition.endsWith("が偽")) {
                    String base = innerCondition.substring(0, innerCondition.length() - 2);
                    return base + "が真";
                }

                // AND/OR を含む場合は全体を括弧で囲んでから否定を付与
                if (innerCondition.contains(" かつ ") || innerCondition.contains(" または ")) {
                    String wrapped = innerCondition;
                    if (!innerCondition.startsWith("(" ) || !innerCondition.endsWith(")")) {
                        wrapped = "(" + innerCondition + ")";
                    }
                    return wrapped + "でない";
                }

                // その他の場合はそのまま否定を付与
                return innerCondition + "でない";
            }
        }

        if (condition instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) condition;
            return convertConditionDetailed(binary, wrapInParentheses, wrapInnerConditions);
        } else if (condition instanceof BooleanLiteralExpr) {
            boolean value = ((BooleanLiteralExpr) condition).getValue();
            return value ? "真" : "偽";
        } else if (condition instanceof NameExpr) {
            // 単独のブール変数の場合は「変数名が真」に変換
            return ((NameExpr) condition).getNameAsString() + "が真";
        } else if (condition instanceof com.github.javaparser.ast.expr.InstanceOfExpr) {
            // instanceof演算子の処理
            com.github.javaparser.ast.expr.InstanceOfExpr instanceOf = 
                (com.github.javaparser.ast.expr.InstanceOfExpr) condition;
            String objectName = convertExpressionToString(instanceOf.getExpression());
            String typeName = instanceOf.getType().toString();
            if (instanceOf.getPattern().isPresent()) {
                String patternVar = ((com.github.javaparser.ast.expr.TypePatternExpr)instanceOf.getPattern().get()).getNameAsString();
                return objectName + "が" + typeName + "型で" + patternVar + "に代入できる";
            }
            return objectName + "が" + typeName + "型";
        } else {
            // その他の式(フィールドアクセスなど)も「〜が真」に変換
            return condition.toString() + "が真";
        }
    }

    private static String convertConditionDetailed(BinaryExpr binary, boolean wrapInParentheses, boolean wrapInnerConditions) { // 二項演算式の変換
        switch (binary.getOperator()) { // 演算子によって変換
            case EQUALS: // 等しい
                return ExpressionConverter.convertExpression(binary.getLeft()) + " = " + ExpressionConverter.convertExpression(binary.getRight());
            case NOT_EQUALS: // 等しくない
                return ExpressionConverter.convertExpression(binary.getLeft()) + " ≠ " + ExpressionConverter.convertExpression(binary.getRight());
            case LESS: // より小さい
                return ExpressionConverter.convertExpression(binary.getLeft()) + " < " + ExpressionConverter.convertExpression(binary.getRight());
            case LESS_EQUALS: // 以下
                return ExpressionConverter.convertExpression(binary.getLeft()) + " ≤ " + ExpressionConverter.convertExpression(binary.getRight());
            case GREATER: // より大きい
                return ExpressionConverter.convertExpression(binary.getLeft()) + " > " + ExpressionConverter.convertExpression(binary.getRight());
            case GREATER_EQUALS: // 以上
                return ExpressionConverter.convertExpression(binary.getLeft()) + " ≥ " + ExpressionConverter.convertExpression(binary.getRight());
            case AND: // かつ
                // 各条件を変換
                String leftCondition = convertCondition(binary.getLeft(), wrapInnerConditions, wrapInnerConditions);
                String rightCondition = convertCondition(binary.getRight(), wrapInnerConditions, wrapInnerConditions);
                if (wrapInParentheses) {
                    if (wrapInnerConditions) {
                        return "((" + leftCondition + ") かつ (" + rightCondition + "))";
                    } else {
                        return "(" + leftCondition + " かつ " + rightCondition + ")";
                    }
                } else {
                    if (wrapInnerConditions) {
                        return "(" + leftCondition + ") かつ (" + rightCondition + ")";
                    } else {
                        return leftCondition + " かつ " + rightCondition;
                    }
                }
            case OR: // または
                // 各条件を変換
                String leftConditionOr = convertCondition(binary.getLeft(), wrapInnerConditions, wrapInnerConditions);
                String rightConditionOr = convertCondition(binary.getRight(), wrapInnerConditions, wrapInnerConditions);
                if (wrapInParentheses) {
                    if (wrapInnerConditions) {
                        return "((" + leftConditionOr + ") または (" + rightConditionOr + "))";
                    } else {
                        return "(" + leftConditionOr + " または " + rightConditionOr + ")";
                    }
                } else {
                    if (wrapInnerConditions) {
                        return "(" + leftConditionOr + ") または (" + rightConditionOr + ")";
                    } else {
                        return leftConditionOr + " または " + rightConditionOr;
        }
    }
            default: // その他
                return convertExpressionToString(binary.getLeft()) + "が" + convertExpressionToString(binary.getRight());
        }
    }

    public static String convertExpressionToString(Expression expr) { // 式を文字列に変換
        if (expr instanceof BooleanLiteralExpr) { // ブールリテラルの場合
            boolean value = ((BooleanLiteralExpr) expr).getValue();
            return value ? "真" : "偽";
        } else if (expr instanceof StringLiteralExpr) { // 文字列リテラルの場合
            return "「" + ((StringLiteralExpr) expr).asString() + "」";
        } else if (expr instanceof IntegerLiteralExpr) { // 整数リテラルの場合
            return ((IntegerLiteralExpr) expr).getValue();
        } else if (expr instanceof NameExpr) { // 変数名の場合
            return ((NameExpr) expr).getNameAsString();
        } else if (expr.isThisExpr()) { // thisキーワードの場合
            return "自身";
        } else if (expr.isFieldAccessExpr()) { // フィールドアクセスの場合
            FieldAccessExpr fae = expr.asFieldAccessExpr();
            String scope = convertExpressionToString(fae.getScope());
            if ("自身".equals(scope)) {
                return scope + fae.getNameAsString(); // "自身のname"
            }
            return scope + "の" + fae.getNameAsString(); // "hのname"
        } else if (expr instanceof MethodCallExpr) { // メソッド呼び出しの場合
            MethodCallExpr mc = (MethodCallExpr) expr;
            String mname = mc.getNameAsString();

            // equals / equalsIgnoreCase -> "left と right が等しい"
            if (("equals".equals(mname) || "equalsIgnoreCase".equals(mname)) && mc.getArguments().size() == 1) {
                String left = mc.getScope().map(e -> convertExpressionToString(e)).orElse(mc.getScope().map(Object::toString).orElse(""));
                String right = convertExpressionToString(mc.getArgument(0));
                return left + "と" + right + "が等しい";
            }

            // contains -> "scope に 「arg」が含まれている"
            if ("contains".equals(mname) && mc.getArguments().size() == 1) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse(mc.getScope().map(Object::toString).orElse(""));
                String arg = convertExpressionToString(mc.getArgument(0));
                return scope + "に" + arg + "が含まれている";
            }

            // endsWith -> "scope が 「arg」で終わる"
            if ("endsWith".equals(mname) && mc.getArguments().size() == 1) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse(mc.getScope().map(Object::toString).orElse(""));
                String arg = convertExpressionToString(mc.getArgument(0));
                return scope + "が" + arg + "で終わる";
            }

            // isEmpty() -> "scope の長さが0"
            if ("isEmpty".equals(mname) && mc.getArguments().isEmpty()) {
                String scope = mc.getScope().map(e -> convertExpressionToString(e)).orElse(mc.getScope().map(Object::toString).orElse(""));
                return scope + "の長さが0";
            }

            // それ以外のメソッド呼び出しはそのまま文字列化
            String scopePrefix = mc.getScope().map(Object::toString).map(s -> s + "の").orElse("");
            String args = mc.getArguments().stream().map(a -> convertExpressionToString(a)).reduce((a,b) -> a + ", " + b).orElse("");
            if (args.isEmpty()) {
                return scopePrefix + mc.getNameAsString() + "()";
            } else {
                return scopePrefix + mc.getNameAsString() + "(" + args + ")";
            }
        }
        return expr.toString();
    }
}