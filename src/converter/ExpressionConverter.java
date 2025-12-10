package converter;

import java.util.stream.Collectors;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;

/**
 * 式(Expression)をなでしこ風の日本語表現に変換するクラス
 * 文字列リテラル、数値リテラル、メソッド呼び出し、演算などを処理
 */
public class ExpressionConverter {

    /**
     * 式を日本語表現に変換
     * 
     * @param expression 変換対象の式
     * @return 変換後の文字列、変換できない場合はnull
     */
    public static String convertExpression(Expression expression) {
        Expression expr = unwrap(expression);

        // --- 右辺が代入式の場合 (例: c = (a = 20)) ---
        if (expr instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) expr;
            // 通常の代入式(=)で、かつ親が式の一部である場合のみ、入れ子代入として特別処理
            if (assignExpr.getOperator() == AssignExpr.Operator.ASSIGN) {
                if (expression.getParentNode().isPresent() && !(expression.getParentNode().get() instanceof com.github.javaparser.ast.stmt.ExpressionStmt)) {
                    String innerVarName = assignExpr.getTarget().toString();
                    Expression innerValue = assignExpr.getValue();
                    
                    // 右辺をconvertExpressionで再帰的に変換（ネストした代入式に対応）
                    String convertedValue = convertExpression(innerValue);
                    if (convertedValue == null) {
                        convertedValue = innerValue.toString();
                    }
                    
                    return "(" + innerVarName + " は " + convertedValue + ")";
                }
            }
        }

        // --- 右辺が条件式の場合、ConditionConverterで変換を試みる ---
        boolean isConditionalExpr = false;
        if (expr instanceof BinaryExpr) {
            BinaryExpr.Operator op = ((BinaryExpr) expr).getOperator();
            isConditionalExpr = op == BinaryExpr.Operator.EQUALS || op == BinaryExpr.Operator.NOT_EQUALS ||
                                op == BinaryExpr.Operator.LESS || op == BinaryExpr.Operator.LESS_EQUALS ||
                                op == BinaryExpr.Operator.GREATER || op == BinaryExpr.Operator.GREATER_EQUALS ||
                                op == BinaryExpr.Operator.AND || op == BinaryExpr.Operator.OR;
        } else if (expr instanceof UnaryExpr && ((UnaryExpr) expr).getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
            isConditionalExpr = true;
        } else if (expr instanceof MethodCallExpr) {
            isConditionalExpr = true; // メソッド呼び出しは条件の可能性がある
        }
        if (isConditionalExpr) {
            String conditionText = ConditionConverter.convertCondition(expr);
            if (conditionText != null && !conditionText.endsWith("が真")) {
                return "<" + conditionText + ">";
            }
        }

        // instanceof の処理
        if (expr instanceof InstanceOfExpr) {
            InstanceOfExpr instanceOfExpr = (InstanceOfExpr) expr;
            String objectName = convertExpression(instanceOfExpr.getExpression());
            String typeName = instanceOfExpr.getType().toString();
            if (instanceOfExpr.getPattern().isPresent()) {
                String patternVar = instanceOfExpr.getPattern().get().toString();
                return "(" + objectName + "が" + typeName + "型で" + patternVar + "に代入できる)";
            }
            return objectName + "が" + typeName + "型";
        }

        // 複合代入演算子の処理
        if (expr instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) expr;
            if (assignExpr.getOperator() != AssignExpr.Operator.ASSIGN) {
                String target = convertTargetExpression(assignExpr.getTarget());
                Expression value = assignExpr.getValue();
                String operatorStr = convertCompoundOperator(assignExpr.getOperator());

                // 右辺がさらに代入式の場合 (例: y += (x += 3))
                Expression unwrappedValue = unwrap(value);
                if (unwrappedValue instanceof AssignExpr) {
                    AssignExpr innerAssign = (AssignExpr) unwrappedValue;
                    String innerTarget = innerAssign.getTarget().toString();
                    Expression innerValue = innerAssign.getValue();
                    
                    // 内側の代入式を変換
                    String innerConverted;
                    if (innerAssign.getOperator() == AssignExpr.Operator.ASSIGN) {
                        // 通常の代入 (x = 4)
                        String innerValueStr = convertExpression(innerValue);
                        if (innerValueStr == null) {
                            innerValueStr = innerValue.toString();
                        }
                        innerConverted = innerTarget + " は " + innerValueStr;
                    } else {
                        // 複合代入 (x += 3)
                        String innerOp = convertCompoundOperator(innerAssign.getOperator());
                        String innerValueStr = convertExpression(innerValue);
                        if (innerValueStr == null) {
                            innerValueStr = innerValue.toString();
                        }
                        innerConverted = innerTarget + " は " + innerTarget + " " + innerOp + " " + innerValueStr;
                    }
                    
                    return "(" + target + " " + operatorStr + " (" + innerConverted + "))";
                } else {
                    String valueStr = convertExpression(value);
                    if (valueStr == null) {
                        valueStr = value.toString();
                    }
                    return "(" + target + " " + operatorStr + " " + valueStr + ")";
                }
            }
        }

        // 単項演算子(インクリメント/デクリメント)の処理
        if (expr instanceof UnaryExpr) {
            UnaryExpr unaryExpr = (UnaryExpr) expr;
            // 式の一部として評価される場合（例：println(++a)）
            if (expression.getParentNode().isPresent() && !(expression.getParentNode().get() instanceof com.github.javaparser.ast.stmt.ExpressionStmt)) {
                String variableName = unaryExpr.getExpression().toString();
                if (unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT ||
                    unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT) {
                    return "(" + variableName + " + 1)";
                } else if (unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT ||
                           unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT) {
                    return "(" + variableName + " - 1)";
                }
            } else { // 文として評価される場合 (例: a++;)
                String varName = unaryExpr.getExpression().toString();
                if (unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT || unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT) {
                    return "(" + varName + " + 1)";
                } else if (unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT || unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT) {
                    return "(" + varName + " - 1)";
                }
            }
        }

        // 文字列連結の処理
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator() == BinaryExpr.Operator.PLUS && containsStringLiteral(binary)) {
                return convertStringConcatenation(binary);
            }
        }

        // 二項演算の処理
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            
            // 二項演算の左辺または右辺に代入式が含まれる場合の処理
            Expression left = binary.getLeft();
            Expression right = binary.getRight();
            Expression unwrappedLeft = unwrap(left);
            Expression unwrappedRight = unwrap(right);
            
            boolean leftHasAssign = unwrappedLeft instanceof AssignExpr;
            boolean rightHasAssign = unwrappedRight instanceof AssignExpr;
            
            if (leftHasAssign || rightHasAssign) {
                String leftStr = convertExpression(left);
                if (leftStr == null) {
                    leftStr = left.toString();
                }
                String rightStr = convertExpression(right);
                if (rightStr == null) {
                    rightStr = right.toString();
                }
                String operator = convertBinaryOperator(binary.getOperator());
                if (operator != null) {
                    return "(" + leftStr + " " + operator + " " + rightStr + ")";
                }
            }
            
            String binaryResult = convertBinaryExpression(binary);
            if (binaryResult != null) {
                return binaryResult;
            }

            // 上記の特殊ケースに当てはまらない、一般的な二項演算の処理
            String leftStr = convertExpression(left);
            if (leftStr == null) {
                leftStr = left.toString();
            }
            String rightStr = convertExpression(right);
            if (rightStr == null) {
                rightStr = right.toString();
            }
            String operator = convertBinaryOperator(binary.getOperator());
            if (operator != null) {
                // 親ノードも同じ優先順位の演算子なら括弧は不要
                boolean needsParen = true;
                if (expression.getParentNode().isPresent() && expression.getParentNode().get() instanceof BinaryExpr) {
                    BinaryExpr parentExpr = (BinaryExpr) expression.getParentNode().get();
                    // 親が+か-で、自分も+か-なら括弧不要
                    if ((parentExpr.getOperator() == BinaryExpr.Operator.PLUS || parentExpr.getOperator() == BinaryExpr.Operator.MINUS) &&
                        (binary.getOperator() == BinaryExpr.Operator.PLUS || binary.getOperator() == BinaryExpr.Operator.MINUS)) {
                        needsParen = false;
                    }
                }
                return needsParen ? "(" + leftStr + " " + operator + " " + rightStr + ")" : leftStr + " " + operator + " " + rightStr;
            }
        }

        // オブジェクト生成の処理
        if (expr instanceof ObjectCreationExpr) {
            return convertObjectCreation((ObjectCreationExpr) expr);
        }

        // メソッド呼び出しの処理
        if (expr instanceof MethodCallExpr) {
            String result = MethodConverter.convertMethodCallExpression((MethodCallExpr) expr);
            if (result != null) {
                return result;
            }
        }

        // 配列の.lengthプロパティを「の配列要素数」に変換
        if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr fae = (FieldAccessExpr) expr;
            if ("length".equals(fae.getNameAsString())) {
                String scope = convertExpression(fae.getScope());
                return scope + "の配列要素数";
            }
            // this.field を「自身のfield」に変換
            if (fae.getScope().isThisExpr()) {
                return "自身の" + fae.getNameAsString();
            } else {
                return convertExpression(fae.getScope()) + "の" + fae.getNameAsString();
            }
        }

        // Math.random() 単体の場合
        if (isMathRandom(expr)) {
            return "1の実数乱数";
        }

        // thisキーワードを「自身」に変換
        if (expr.isThisExpr()) {
            return "自身";
        }

        // --- Math.random() を含む掛け算パターン ---
        if (expr instanceof BinaryExpr) {
            // このロジックは convertBinaryExpression に集約されているため、ここでは不要
        }

        // 文字列/テキストブロック/char の処理
        if (expr instanceof StringLiteralExpr) {
            String valueStr = getStringLiteralValue((StringLiteralExpr) expr);
            return "「" + valueStr + "」";
        } else if (expr instanceof TextBlockLiteralExpr) {
            String valueStr = getTextBlockValue((TextBlockLiteralExpr) expr);
            return "「" + valueStr + "」";
        } else if (expr instanceof CharLiteralExpr) {
            String valueStr = getCharLiteralValue((CharLiteralExpr) expr);
            return "「" + valueStr + "」";
        }

        // 数値や単純な式の文字列化
        String valueStr = getValueString(expr);
        if (valueStr != null) {
            return valueStr;
        }
        return null;
    }

    private static String convertTargetExpression(Expression target) {
        if (target.isFieldAccessExpr()) {
            var fae = target.asFieldAccessExpr();
            var scope = fae.getScope();
            if (scope.isThisExpr()) {
                return "自身の" + fae.getNameAsString();
            } else {
                // obj.field のようなケース
                return scope.toString() + "の" + fae.getNameAsString();
            }
        }
        // 単純な変数 a = 10;
        return target.toString();
    }


    private static String convertCompoundOperator(AssignExpr.Operator operator) {
        return getOperatorString(operator.name());
    }
    
    private static String convertBinaryOperator(BinaryExpr.Operator operator) {
        return getOperatorString(operator.name());
    }

    /**
     * 演算子の名前(PLUS, MINUSなど)から対応する記号(+, -など)を取得する共通メソッド
     * @param operatorName 演算子の名前
     * @return 演算子の記号
     */
    private static String getOperatorString(String operatorName) {
        return switch (operatorName) {
            case "PLUS" -> "+";
            case "MINUS" -> "-";
            case "MULTIPLY" -> "*";
            case "DIVIDE" -> "/";
            case "REMAINDER" -> "%";
            default -> null;
        };
    }
    /**
     * 二項演算式を変換
     */
    private static String convertBinaryExpression(BinaryExpr binary) {
        // 掛け算: Math.random() を含む場合
        if (binary.getOperator() == BinaryExpr.Operator.MULTIPLY) {
            Expression left = unwrap(binary.getLeft());
            Expression right = unwrap(binary.getRight());

            if (isMathRandom(left)) {
                return "1の実数乱数 * " + convertExpression(binary.getRight());
            }
            if (isMathRandom(right)) {
                return convertExpression(binary.getLeft()) + " * 1の実数乱数";
            }
        }

        // Random.nextInt(...) + k のパターン
        if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Expression left = binary.getLeft();
            Expression right = binary.getRight();

            if (left instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) left;
                if ("nextInt".equals(methodCall.getNameAsString())) {
                    String scopeStr = methodCall.getScope().map(Expression::toString).orElse("");
                    if (scopeStr.contains("Random")) {
                        if (methodCall.getArguments().size() == 1) {
                            String maxValue = methodCall.getArgument(0).toString();
                            String rightStr = getValueString(right);
                            if (rightStr != null) {
                                return maxValue + "の乱数+" + rightStr;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * オブジェクト生成を変換
     */
    public static String convertObjectCreation(ObjectCreationExpr objCreation) {
        // ジェネリクスを除去したクラス名を取得
        String className;
        if (objCreation.getType().isClassOrInterfaceType()) {
            className = objCreation.getType()
                    .asClassOrInterfaceType()
                    .getName()
                    .getIdentifier(); // ← これでジェネリクス部分は含まれない
        } else {
            String rawType = objCreation.getTypeAsString();
            className = rawType.replaceAll("<.*?>", "");
        }

        // Date の生成
        if ("Date".equals(className)) {
            if (objCreation.getArguments().isEmpty()) {
                return "現在日時";
            }
            if (objCreation.getArguments().size() == 1) {
                Expression arg = unwrap(objCreation.getArgument(0));
                String millis = getValueString(arg) != null ? getValueString(arg) : arg.toString();
                return millis + "ミリ秒日時";
            }
        }

        // FileReader の生成
        if ("FileReader".equals(className)) {
            if (objCreation.getArguments().size() == 1) {
                String arg = convertArgument(objCreation.getArgument(0));
                return "ファイル" + arg + "生成";
            }
        }

        String args = objCreation.getArguments().stream().map(ExpressionConverter::convertArgument).collect(Collectors.joining(", "));

        if (className.contains("Random")) {
            return "乱数生成器";
        }
        if (className.contains("Scanner")) {
            return "入力器";
        }
        if (className.contains("Calendar")) {
            return "カレンダー生成";
        }

        // Swingコンポーネントの変換
        if ("JFrame".equals(className)) {
            className = "フレーム";
        }
        if ("JLabel".equals(className)) {
            className = "ラベル";
        }
        if ("JButton".equals(className)) {
            className = "ボタン";
        }
        if ("FlowLayout".equals(className)) {
            if (args.isEmpty()) {
                return "FlowLayout"; // new FlowLayout() -> FlowLayout
            }
        }

        // Stringを文字列に変換
        if ("String".equals(className)) {
            className = "文字列";
        }

        if (args.isEmpty()) {
            return className + "生成";
        } else {
            return className + "(" + args + ")生成";
        }
    }

    // ========== ユーティリティメソッド ==========



    /**
     * CastExpr / EnclosedExpr を剥がす
     */
    private static Expression unwrap(Expression e) {
        Expression cur = e;
        boolean progress = true;
        while (progress) {
            progress = false;
            if (cur instanceof CastExpr) {
                cur = ((CastExpr) cur).getExpression();
                progress = true;
                continue;
            }
            if (cur instanceof EnclosedExpr) {
                cur = ((EnclosedExpr) cur).getInner();
                progress = true;
                continue;
            }
        }
        return cur;
    }

    /**
     * 式の中に Math.random() が含まれているか判定
     */
    private static boolean isMathRandom(Expression e) {
        if (e == null)
            return false;
        e = unwrap(e);
        if (e instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) e;
            if ("random".equals(mc.getNameAsString())) {
                String scope = mc.getScope().map(Expression::toString).orElse("");
                return "Math".equals(scope);
            }
            return false;
        } else if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            return isMathRandom(b.getLeft()) || isMathRandom(b.getRight());
        }
        return false;
    }

    /**
     * 文字列リテラルの値を取得(エスケープシーケンス変換込み)
     */
    public static String getStringLiteralValue(StringLiteralExpr expr) {
        String originalCode = expr.toString();
        if (originalCode.startsWith("\"") && originalCode.endsWith("\"")) {
            String content = originalCode.substring(1, originalCode.length() - 1);
            return convertJavaEscapeSequences(content);
        }
        return expr.asString();
    }

    /**
     * Charリテラルの値を取得(エスケープシーケンス変換込み)
     */
    public static String getCharLiteralValue(CharLiteralExpr expr) {
        String originalCode = expr.toString();
        if (originalCode.startsWith("'") && originalCode.endsWith("'")) {
            String content = originalCode.substring(1, originalCode.length() - 1);
            return convertJavaEscapeSequences(content);
        }
        return String.valueOf(expr.asChar());
    }

    /**
     * テキストブロックの値を取得
     */
    public static String getTextBlockValue(TextBlockLiteralExpr expr) {
        String value = expr.getValue();
        if (value.isEmpty()) {
            return value;
        }
        String[] lines = value.split("\n", -1);
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int indent = 0;
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == ' ' || c == '\t' || c == '　') {
                        indent++;
                    } else {
                        break;
                    }
                }
                minIndent = Math.min(minIndent, indent);
            }
        }
        int lastNonEmptyLine = lines.length - 1;
        while (lastNonEmptyLine >= 0 && lines[lastNonEmptyLine].trim().isEmpty()) {
            lastNonEmptyLine--;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i <= lastNonEmptyLine; i++) {
            String line = lines[i];
            if (minIndent != Integer.MAX_VALUE && minIndent > 0 && line.length() >= minIndent) {
                line = line.substring(minIndent);
            }
            line = line.replaceAll("[ 　\t]+$", "");
            if (i > 0) {
                result.append("{改行}");
            }
            result.append(line);
        }
        return result.toString();
    }

    /**
     * 式から値の文字列表現を取得
     */
    public static String getValueString(Expression expression) {
        Expression expr = unwrap(expression);
        if (expr instanceof StringLiteralExpr) {
            String originalCode = expr.toString();
            if (originalCode.startsWith("\"") && originalCode.endsWith("\"")) {
                return originalCode.substring(1, originalCode.length() - 1);
            }
            return ((StringLiteralExpr) expr).asString();
        } else if (expr instanceof IntegerLiteralExpr) {
            return ((IntegerLiteralExpr) expr).getValue();
        } else if (expr instanceof LongLiteralExpr) {
            String value = ((LongLiteralExpr) expr).getValue();
            return value.replaceAll("[Ll]$", "");
        } else if (expr instanceof DoubleLiteralExpr) {
            return String.valueOf(((DoubleLiteralExpr) expr).asDouble());
        } else if (expr instanceof BooleanLiteralExpr) {
            boolean value = ((BooleanLiteralExpr) expr).getValue();
            return value ? "真" : "偽";
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            if (unary.getOperator() == UnaryExpr.Operator.MINUS) {
                if (unary.getExpression() instanceof IntegerLiteralExpr) {
                    String value = ((IntegerLiteralExpr) unary.getExpression()).getValue();
                    return "-" + value;
                } else if (unary.getExpression() instanceof LongLiteralExpr) {
                    String value = ((LongLiteralExpr) unary.getExpression()).getValue();
                    String cleanValue = value.replaceAll("[Ll]$", "");
                    return "-" + cleanValue;
                } else if (unary.getExpression() instanceof DoubleLiteralExpr) {
                    double value = ((DoubleLiteralExpr) unary.getExpression()).asDouble();
                    return "-" + value;
                }
            }
        } else if (expr instanceof BinaryExpr) {
            return null;
        } else {
            String value = expr.toString();
            value = value.replaceAll("([0-9]+)[Ll]", "$1");
            value = value.replaceAll("([0-9]*\\.?[0-9]+)[FfDd]", "$1");
            return value;
        }
        return null;
    }

    /**
     * Javaのエスケープシーケンスを日本語表記に変換
     */
    public static String convertJavaEscapeSequences(String javaString) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < javaString.length()) {
            if (javaString.charAt(i) == '\\' && i + 1 < javaString.length()) {
                char nextChar = javaString.charAt(i + 1);
                switch (nextChar) {
                    case 'n':
                        result.append("{改行}");
                        i += 2;
                        break;
                    case 't':
                        result.append("{タブ}");
                        i += 2;
                        break;
                    case '"':
                        result.append('"');
                        i += 2;
                        break;
                    case '\'':
                        result.append('\'');
                        i += 2;
                        break;
                    case '\\':
                        result.append('\\');
                        i += 2;
                        break;
                    default:
                        result.append(javaString.charAt(i));
                        i++;
                        break;
                }
            } else {
                result.append(javaString.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    // 引数を日本語形式に変換
    private static String convertArgument(Expression expr) {
        if (expr instanceof StringLiteralExpr) {
            StringLiteralExpr strExpr = (StringLiteralExpr) expr;
            return "「" + strExpr.getValue() + "」";
        } else if (expr instanceof IntegerLiteralExpr || expr instanceof LongLiteralExpr) {
            return expr.toString();
        }
        return expr.toString();
    }

    // --- 文字列連結のためのヘルパーメソッド (PrintlnConverterから移動) ---

    public static boolean containsStringLiteral(Expression expr) {
        if (expr instanceof StringLiteralExpr) {
            return true;
        }
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
                return containsStringLiteral(binary.getLeft()) || containsStringLiteral(binary.getRight());
            }
        }
        return false;
    }

    public static String convertStringConcatenation(BinaryExpr binary) {
        StringBuilder result = new StringBuilder();
        result.append("「");
        collectConcatenation(binary, result);
        result.append("」");
        return result.toString();
    }

    private static void collectConcatenation(Expression expr, StringBuilder sb) {
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
                collectConcatenation(binary.getLeft(), sb);
                collectConcatenation(binary.getRight(), sb);
                return;
            }
        }
        if (expr instanceof StringLiteralExpr) {
            String valueStr = getStringLiteralValue((StringLiteralExpr) expr);
            sb.append(valueStr);
        } else if (expr instanceof IntegerLiteralExpr || expr instanceof DoubleLiteralExpr) {
            String value = getValueString(expr);
            sb.append("{").append(value).append("}");
        } else if (expr instanceof NameExpr) {
            sb.append("{").append(((NameExpr) expr).getNameAsString()).append("}");
        } else if (expr instanceof FieldAccessExpr) {
            sb.append("{").append(convertExpression(expr)).append("}");
        } else if (expr instanceof MethodCallExpr) {
            // Math.maxなどを変換するためにMethodConverterを呼び出す
            String converted = MethodConverter.convertMethodCallExpression((MethodCallExpr) expr);
            if (converted != null) {
                sb.append("{").append(converted).append("}");
            } else {
                sb.append("{").append(expr.toString()).append("}");
            }
        } else {
            // その他の式はそのまま埋め込む
            sb.append("{").append(expr.toString()).append("}");
        }
    }
}