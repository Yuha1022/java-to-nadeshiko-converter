package converter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import converter.IfStatementConverter.Item;
import converter.MethodConverter.MethodVisitor;

/**
 * Javaのメソッド・コンストラクタ・メソッド呼び出しを日本語に変換するクラス
 */
public class MethodConverter {

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        cu.accept(new MethodVisitor(items), null);
        return items;
    }

    /**
     * メソッドとコンストラクタを訪問するVisitor
     */
    public static class MethodVisitor extends VoidVisitorAdapter<Void> {
        private final List<Item> items;

        public MethodVisitor(List<Item> items) {
            this.items = items;
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            int line = method.getBegin().map(p -> p.line).orElse(-1);
            String outerIndent = IndentManager.getIndentForLine(line);
            String methodName = method.getNameAsString();
            String params = formatParameters(method.getParameters());

            // メソッド宣言前のコメント行のインデントを記録
            int bodyStartLine = method.getBody().map(b -> b.getBegin().map(p -> p.line).orElse(line)).orElse(line);
            String bodyIndent = outerIndent + "　";
            for (int i = line; i < bodyStartLine; i++) {
                // メソッド宣言自体の行は除外
                IndentManager.recordIndentForLine(i, outerIndent);
            }

            // メソッド開始
            if (methodName.equals("main")) {
                items.add(new Item(line, outerIndent + "関数　メイン関数とは", 20));
            } else {
                String declaration = params.isEmpty()
                        ? "関数 " + methodName + "とは"
                        : "関数 " + methodName + "(" + params + ")とは";
                items.add(new Item(line, outerIndent + declaration, 10));
            }

            // メソッド本体を再帰的に処理
            if (method.getBody().isPresent()) {
                // 本体があるメソッド
                processBlock(method.getBody().get(), bodyIndent);
                // メソッド終了
                addMethodEnd(method.getBody().get(), method.getEnd().map(p -> p.line).orElse(-1), outerIndent);
            } else {
                // 本体がないメソッド (abstract や interface)
                items.add(new Item(line, outerIndent + "ここまで。", 50));
            }

            super.visit(method, arg);
        }

        @Override
        public void visit(ConstructorDeclaration constructor, Void arg) {
            int line = constructor.getBegin().map(p -> p.line).orElse(-1);
            String outerIndent = IndentManager.getIndentForLine(line);
            String className = constructor.getNameAsString();
            String params = formatParameters(constructor.getParameters());

            // コンストラクタ開始
            String declaration = params.isEmpty()
                    ? className + "生成時"
                    : className + "(" + params + ")生成時";
            items.add(new Item(line, outerIndent + declaration, 20));

            // コンストラクタ本体を再帰的に処理
            processBlock(constructor.getBody(), outerIndent + "　");

            // コンストラクタ終了
            addMethodEnd(constructor.getBody(), constructor.getBody().getEnd().map(p -> p.line).orElse(-1),
                    outerIndent);

            super.visit(constructor, arg);
        }

        @Override
        public void visit(ExplicitConstructorInvocationStmt stmt, Void arg) {
            int line = stmt.getBegin().map(p -> p.line).orElse(-1);
            String outerIndent = IndentManager.getIndentForLine(line); // 外側のインデントを取得
            String args = formatArguments(stmt.getArguments());

            String invocation;
            if (stmt.isThis()) {
                // this(msg) -> 自身のコンストラクタ(msg)。
                String thisCall = "自身のコンストラクタ";
                invocation = args.isEmpty() ? thisCall + "。" : thisCall + "(" + args + ")。";
            } else { // super()
                // super(msg) -> 親のコンストラクタ(msg)。
                String superCall = "親のコンストラクタ";
                invocation = args.isEmpty() ? superCall + "。" : superCall + "(" + args + ")。";
            }

            items.add(new Item(line, outerIndent + invocation, 30));
            super.visit(stmt, arg);
        }

        @Override
        public void visit(ReturnStmt stmt, Void arg) {
            if (isInsideIfThen(stmt)) {
                super.visit(stmt, arg);
                return;
            }

            int line = stmt.getBegin().map(p -> p.line).orElse(-1);

            if (stmt.getExpression().isPresent()) {
                Expression returnExpr = stmt.getExpression().get();
                String returnValue = convertReturnExpression(returnExpr);
                String indent = IndentManager.getIndentForLine(line);
                items.add(new Item(line, indent + returnValue + "を戻す。", 35));
            }

            super.visit(stmt, arg);
        }

        @Override
        public void visit(ExpressionStmt stmt, Void arg) {
            Expression expr = stmt.getExpression();
            if (expr.isMethodCallExpr()) {
                MethodCallExpr methodCall = expr.asMethodCallExpr();
                if (!shouldSkipMethodCall(methodCall)) {
                    int line = methodCall.getBegin().map(p -> p.line).orElse(-1);
                    String converted = convertMethodCallToJapanese(methodCall, line);
                    if (converted != null) {
                        String indent = IndentManager.getIndentForLine(line);
                        items.add(new Item(line, indent + converted, 30));
                    }
                    // MethodCallExprを処理したので、子ノードの訪問はスキップする
                    return;
                }
            }
            // MethodCallExpr以外の場合は、通常通り子ノードを訪問する
            super.visit(stmt, arg);
        }
        // ヘルパーメソッド群

        /**
         * ブロック内の各文を処理し、インデントを記録する
         */
        public static void processBlock(BlockStmt block, String indent) {
            if (block == null) return;
            int lastLine = block.getBegin().map(p -> p.line).orElse(0);

            for (Statement stmt : block.getStatements()) {
                int startLine = stmt.getBegin().map(p -> p.line).orElse(-1);
                if (startLine != -1) {
                    // 前の文の終わりから今の文の始まりまで(コメント行や空行)をインデント
                    for (int i = lastLine + 1; i < startLine; i++) {
                        IndentManager.recordIndentForLine(i, indent);
                    }
                    IndentManager.recordIndentForLine(startLine, indent);
                    lastLine = stmt.getEnd().map(p -> p.line).orElse(startLine);
                }

                // 制御構文の場合は、さらにその中身を再帰的に処理
                if (stmt instanceof IfStmt) {
                    IfStmt ifStmt = (IfStmt) stmt;
                    // then ブロック
                    if (ifStmt.getThenStmt() instanceof BlockStmt) {
                        processBlock(ifStmt.getThenStmt().asBlockStmt(), indent + "　");
                    } else {
                        ifStmt.getThenStmt().getBegin().ifPresent(p -> IndentManager.recordIndentForLine(p.line, indent + "　"));
                    }
                    // else ブロック
                    ifStmt.getElseStmt().ifPresent(elseStmt -> {
                        if (elseStmt instanceof IfStmt) { // else-if
                            // IfStatementConverterが処理するので何もしない
                        } else { // else
                            if (elseStmt instanceof BlockStmt) {
                                processBlock(elseStmt.asBlockStmt(), indent + "　");
                            } else {
                                elseStmt.getBegin().ifPresent(p -> IndentManager.recordIndentForLine(p.line, indent + "　"));
                            }
                        }
                    });
                } else if (stmt instanceof ForStmt) {
                    ForStmt forStmt = (ForStmt) stmt;
                    processBlock(forStmt.getBody().asBlockStmt(), indent + "　");
                } else if (stmt instanceof com.github.javaparser.ast.stmt.ForEachStmt) {
                    com.github.javaparser.ast.stmt.ForEachStmt forEachStmt = (com.github.javaparser.ast.stmt.ForEachStmt) stmt;
                    processBlock(forEachStmt.getBody().asBlockStmt(), indent + "　");
                } else if (stmt instanceof WhileStmt) {
                    WhileStmt whileStmt = (WhileStmt) stmt;
                    processBlock(whileStmt.getBody().asBlockStmt(), indent + "　");
                } else if (stmt instanceof TryStmt) {
                    TryStmt tryStmt = (TryStmt) stmt;
                    TryCatchConverter.recordBlockIndents(tryStmt, indent);
                }
            }

            // ブロックの最後の文から閉じ括弧までの間のコメント/空行をインデント
            int blockEndLine = block.getEnd().map(p -> p.line).orElse(0);
            for (int i = lastLine + 1; i < blockEndLine; i++) {
                IndentManager.recordIndentForLine(i, indent);
            }
        }

        private String formatParameters(List<Parameter> parameters) {
            return parameters.stream()
                    .map(Parameter::getNameAsString)
                    .collect(Collectors.joining(", "));
        }

        private String formatArguments(NodeList<Expression> arguments) {
            return arguments.stream()
                    .map(expr -> MethodConverter.convertArgument(expr))
                    .collect(Collectors.joining(", "));
        }

        private void addMethodEnd(BlockStmt body, int endLine, String indent) {
            if (body != null && endLine != -1) {
                items.add(new Item(endLine, indent + "ここまで。", 50));
            }
        }

        private boolean shouldSkipMethodCall(MethodCallExpr methodCall) {
            String methodName = methodCall.getNameAsString();

            // println/printは別の場所で処理
            if (methodName.equals("println") || methodName.equals("print")) {
                return true;
            }

            // 他の式の一部として使用されている場合はスキップ
            return isPartOfExpression(methodCall);
        }

        private boolean isPartOfExpression(MethodCallExpr methodCall) {
            if (!methodCall.getParentNode().isPresent()) {
                return false;
            }

            Node parent = methodCall.getParentNode().get();

            // return文の一部
            if (parent instanceof ReturnStmt) {
                return true;
            }

            // if文の条件部
            if (parent instanceof IfStmt) {
                return true;
            }

            // 代入文の一部
            if (parent instanceof AssignExpr ||
                    parent instanceof com.github.javaparser.ast.body.VariableDeclarator) {
                return true;
            }

            // 二項演算式の一部
            if (parent instanceof BinaryExpr) {
                return true;
            }

            // メソッドチェーンの途中
            if (parent instanceof FieldAccessExpr) {
                return true;
            }

            // 他のメソッドの引数
            if (parent instanceof MethodCallExpr) {
                MethodCallExpr parentMethod = (MethodCallExpr) parent; // このキャストはOK
 
                // 引数として使用されているか確認
                for (Expression arg : parentMethod.getArguments()) {
                    if (containsMethodCall(arg, methodCall)) {
                        return true;
                    }
                }

                // スコープとして使用されているか確認
                if (parentMethod.getScope().isPresent() &&
                        containsMethodCall(parentMethod.getScope().get(), methodCall)) {
                    return true;
                }
            }

            return false;
        }

        private boolean containsMethodCall(Expression expr, MethodCallExpr target) {
            if (expr == target) {
                return true;
            }

            if (expr.isMethodCallExpr()) {
                MethodCallExpr mc = expr.asMethodCallExpr();
                for (Expression arg : mc.getArguments()) {
                    if (containsMethodCall(arg, target)) {
                        return true;
                    }
                }
            }
 
            if (expr.isBinaryExpr()) {
                BinaryExpr bin = expr.asBinaryExpr();
                return containsMethodCall(bin.getLeft(), target) ||
                        containsMethodCall(bin.getRight(), target);
            }
            return false;
        }

        private String convertMethodCallToJapanese(MethodCallExpr methodCall, int line) {
            String methodName = methodCall.getNameAsString();
            String scope = methodCall.getScope().map(Expression::toString).orElse("");

            // 特殊なメソッドの処理
            String specialConversion = convertSpecialMethods(methodCall, methodName, scope);
            if (specialConversion != null) {
                return specialConversion;
            }

            // 汎用的な変換を試みる
            String genericConversion = MethodConverter.convertMethodCallExpression(methodCall);
            if (genericConversion != null) {
                return genericConversion + "。";
            }

            // デフォルトの変換
            return convertDefaultMethodCall(methodCall, methodName);
        }

        private String convertSpecialMethods(MethodCallExpr methodCall, String methodName, String scope) {
            // Class.forName
            if ("forName".equals(methodName) && "Class".equals(scope) &&
                    methodCall.getArguments().size() == 1) {
                String arg = convertArgument(methodCall.getArgument(0));
                return arg + "を登録。";
            }

            // executeUpdate
            if ("executeUpdate".equals(methodName) &&
                    methodCall.getScope().isPresent() &&
                    methodCall.getScope().get() instanceof MethodCallExpr) {
                return "connを使用して sql を実行。";
            }

            // Arrays.sort(array)
            if ("sort".equals(methodName) && scope.endsWith("Arrays") && methodCall.getArguments().size() == 1) {
                String arrayName = convertArgument(methodCall.getArgument(0));
                return arrayName + "を配列ソート。";
            }

            // Calendar.getInstance
            if ("getInstance".equals(methodName) && scope.contains("Calendar")) {
                return "カレンダー生成。";
            }

            // Calendar関連のメソッド
            String calendarConversion = convertCalendarMethods(methodCall, methodName);
            if (calendarConversion != null) {
                return calendarConversion;
            }

            // コレクション関連のメソッド
            String collectionConversion = convertCollectionMethods(methodCall, methodName);
            if (collectionConversion != null) {
                return collectionConversion;
            }

            // GUI関連のメソッド
            String guiConversion = convertGuiMethods(methodCall, methodName);
            if (guiConversion != null) {
                return guiConversion;
            }

            // IO関連のメソッド
            String ioConversion = convertIoMethods(methodCall, methodName);
            if (ioConversion != null) {
                return ioConversion;
            }

            return null;
        }

        private String convertCalendarMethods(MethodCallExpr methodCall, String methodName) {
            if (!methodCall.getScope().isPresent()) {
                return null;
            }

            String scopeVar = methodCall.getScope().get().toString();

            // set(year, month, day, hour, min, sec)
            if ("set".equals(methodName) && methodCall.getArguments().size() == 6) {
                List<String> args = methodCall.getArguments().stream()
                        .map(MethodConverter::convertExpressionToString)
                        .collect(Collectors.toList());
                String dateStr = String.format("%s年%s月%s日%s時%s分%s秒",
                        args.get(0), args.get(1), args.get(2), args.get(3), args.get(4), args.get(5));
                return scopeVar + "に " + dateStr + " を設定";
            }

            // set(field, value)
            if ("set".equals(methodName) && methodCall.getArguments().size() == 2) {
                Expression field = methodCall.getArgument(0);
                String value = convertExpressionToString(methodCall.getArgument(1));

                if (field instanceof FieldAccessExpr) {
                    String fieldName = ((FieldAccessExpr) field).getNameAsString();
                    String fieldJp = getCalendarFieldName(fieldName);
                    return scopeVar + "の" + fieldJp + "を" + value + "に設定。";
                }
            }

            // get(field)
            if ("get".equals(methodName) && methodCall.getArguments().size() == 1) {
                Expression field = methodCall.getArgument(0);

                if (field instanceof FieldAccessExpr) {
                    String fieldName = ((FieldAccessExpr) field).getNameAsString();
                    String fieldJp = getCalendarFieldName(fieldName);
                    return scopeVar + "の" + fieldJp;
                }
            }

            // setTime(date)
            if ("setTime".equals(methodName) && methodCall.getArguments().size() == 1) {
                String value = convertExpressionToString(methodCall.getArgument(0));
                return scopeVar + "の日時を " + value + " に設定。";
            }

            return null;
        }

        private String getCalendarFieldName(String fieldName) {
            switch (fieldName) {
                case "YEAR":
                    return "年";
                case "MONTH":
                    return "月";
                case "DAY_OF_MONTH":
                    return "日";
                default:
                    return fieldName;
            }
        }

        private String convertCollectionMethods(MethodCallExpr methodCall, String methodName) {
            if (!methodCall.getScope().isPresent()) {
                return null;
            }

            String scopeVar = methodCall.getScope().get().toString();


            // put(key, value)
            if ("put".equals(methodName) && methodCall.getArguments().size() == 2) {
                String key = convertArgument(methodCall.getArgument(0));
                String value = convertArgument(methodCall.getArgument(1));
                return scopeVar + "に(" + key + ", " + value + ")格納。";
            }

            // remove(key)
            if ("remove".equals(methodName) && methodCall.getArguments().size() == 1) {
                String key = convertArgument(methodCall.getArgument(0));
                return scopeVar + "の" + key + "削除。";
            }

            // frame.getContentPane().add(label) -> frameにlabel追加
            if ("add".equals(methodName) && methodCall.getArguments().size() == 1) {
                if (methodCall.getScope().isPresent()) {
                    Expression scopeExpr = methodCall.getScope().get();
                    if (scopeExpr instanceof MethodCallExpr &&
                            "getContentPane".equals(((MethodCallExpr) scopeExpr).getNameAsString())) {
                        String frameVar = ((MethodCallExpr) scopeExpr).getScope()
                                .map(Object::toString).orElse("");
                        String arg = convertArgument(methodCall.getArgument(0));
                        if (!frameVar.isEmpty()) {
                            return frameVar + "に" + arg + "追加。";
                        }
                    }
                }
                // 上記に一致しない汎用的な add(element)
                String arg = convertArgument(methodCall.getArgument(0));
                return scopeVar + "に" + arg + "追加。";
            }


            return null;
        }

        private String convertGuiMethods(MethodCallExpr methodCall, String methodName) {
            if (!methodCall.getScope().isPresent()) {
                return null;
            }

            String scopeVar = methodCall.getScope().get().toString();

            // setLayout
            if ("setLayout".equals(methodName) && methodCall.getArguments().size() == 1) {
                Expression scopeExpr = methodCall.getScope().get();

                if (scopeExpr instanceof MethodCallExpr &&
                        "getContentPane".equals(((MethodCallExpr) scopeExpr).getNameAsString())) {
                    String frameVar = ((MethodCallExpr) scopeExpr).getScope()
                            .map(Object::toString).orElse("");
                    Expression layoutArg = methodCall.getArgument(0);

                    if (layoutArg instanceof ObjectCreationExpr) {
                        String layoutClass = ((ObjectCreationExpr) layoutArg).getType().getNameAsString();
                        return frameVar + "のレイアウトを " + layoutClass + " に設定。";
                    }
                }
            }

            // setDefaultCloseOperation
            if ("setDefaultCloseOperation".equals(methodName) && methodCall.getArguments().size() == 1) {
                String arg = methodCall.getArgument(0).toString();
                if (arg.endsWith("EXIT_ON_CLOSE")) {
                     return scopeVar + " を 閉じるボタンで終了するように設定。";
                } else {
                    arg = arg.replace("JLabel.", "JFrame.");
                    return scopeVar + "の終了操作を (" + arg + ") に設定。";
                }
            }

            // setSize
            if ("setSize".equals(methodName) && (methodCall.getArguments().size() == 2 || methodCall.getArguments().size() == 1)) {
                String args = formatArguments(methodCall.getArguments());
                return scopeVar + "のサイズを(" + args + ")に設定。";
            }

            // setVisible(true)
            if ("setVisible".equals(methodName) && methodCall.getArguments().size() == 1 &&
                    "true".equals(methodCall.getArgument(0).toString())) {
                return scopeVar + "を表示。";
            }

            return null;
        }

        private String convertIoMethods(MethodCallExpr methodCall, String methodName) {
            if (!methodCall.getScope().isPresent()) {
                return null;
            }

            String scopeVar = methodCall.getScope().get().toString();

            // write
            if ("write".equals(methodName) && methodCall.getArguments().size() == 1) {
                String content = ExpressionConverter.convertExpression(methodCall.getArgument(0));
                return scopeVar + "に" + content + "書込。";
            }

            // close
            if ("close".equals(methodName) && methodCall.getArguments().isEmpty()) {
                return scopeVar + "を閉じる。";
            }

            return null;
        }

        private String convertDefaultMethodCall(MethodCallExpr methodCall, String methodName) {
            String scopePrefix = "";

            if (methodCall.getScope().isPresent()) {
                String scope = methodCall.getScope().get().toString();
                scopePrefix = switch (scope) {
                    case "super" -> "親の";
                    case "this" -> "自身の";
                    default -> scope + "の";
                };
            }

            String args = formatArguments(methodCall.getArguments());

            return args.isEmpty()
                    ? scopePrefix + methodName + "。" // 引数がない場合は()を付けない
                    : scopePrefix + methodName + "(" + args + ")。";
        }

        private String convertReturnExpression(Expression returnExpr) {
            String converted = ExpressionConverter.convertExpression(returnExpr);
            // return new Xxx() の場合、"Xxx生成" -> "Xxx" のように "生成" を削除する
            if (returnExpr.isObjectCreationExpr() && converted != null && converted.endsWith("生成")) {
                // 最後の "生成" のみ削除
                int lastIndex = converted.lastIndexOf("生成");
                return converted.substring(0, lastIndex);
            }

            return converted;
        }

        private boolean isInsideIfThen(ReturnStmt stmt) {
            Node current = stmt;

            while (current != null && current.getParentNode().isPresent()) {
                Node parent = current.getParentNode().get();

                if (parent instanceof IfStmt) {
                    IfStmt ifs = (IfStmt) parent;
                    Statement thenStmt = ifs.getThenStmt();

                    if (containsStatement(thenStmt, stmt)) {
                        return true;
                    } 
                }

                current = parent;
            }

            return false;
        }

        private boolean containsStatement(Statement root, Statement target) {
            if (root == null || root == target) {
                return root == target;
            }

            if (root.isBlockStmt()) {
                for (Statement s : root.asBlockStmt().getStatements()) {
                    if (containsStatement(s, target)) {
                        return true;
                    }
                }
            } else if (root.isIfStmt()) {
                IfStmt ifs = root.asIfStmt();
                if (containsStatement(ifs.getThenStmt(), target)) {
                    return true;
                }
                if (ifs.getElseStmt().isPresent() && containsStatement(ifs.getElseStmt().get(), target)) {
                    return true;
                }
            } else if (root.isForStmt()) {
                Statement body = root.asForStmt().getBody();
                if (containsStatement(body, target)) {
                    return true;
                }
            } else if (root.isWhileStmt()) {
                Statement body = root.asWhileStmt().getBody();
                if (containsStatement(body, target)) {
                    return true;
                }
            }

            return false;
        }

        private String convertArgument(Expression expr) {
            return MethodConverter.convertArgument(expr);
        }

        private String convertExpressionToString(Expression expr) {
            return MethodConverter.convertExpressionToString(expr);
        }
    }

    // 静的ユーティリティメソッド

    private static String convertArgument(Expression expr) {
        if (expr.isStringLiteralExpr()) {
            return "「" + expr.asStringLiteralExpr().getValue() + "」";
        } else if (expr.isIntegerLiteralExpr()) {
            return expr.toString();
        } else if (expr.isCharLiteralExpr()) {
            return "「" + expr.asCharLiteralExpr().getValue() + "」";
        }
        return expr.toString();
    }

    private static String convertExpressionToString(Expression expr) {
        if (expr.isBooleanLiteralExpr()) {
            return expr.asBooleanLiteralExpr().getValue() ? "真" : "偽";
        } else if (expr.isStringLiteralExpr()) {
            return "「" + expr.asStringLiteralExpr().asString() + "」";
        } else if (expr.isIntegerLiteralExpr()) {
            return expr.toString();
        } else if (expr.isNameExpr()) {
            return expr.asNameExpr().getNameAsString();
        } else if (expr.isMethodCallExpr()) {
            return convertMethodCallExpression(expr.asMethodCallExpr());
        }
        return expr.toString();
    }

    public static String convertMethodCallExpression(MethodCallExpr mc) {
        String methodName = mc.getNameAsString();

        // System.currentTimeMillis()
        if ("currentTimeMillis".equals(methodName) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent() && "System".equals(mc.getScope().get().toString())) {
                return "システム時間";
            }
        }

        // ZonedDateTime.now()
        if ("now".equals(methodName) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent()) {
                String scope = mc.getScope().get().toString();
                if ("ZonedDateTime".equals(scope) || "LocalDateTime".equals(scope) || "LocalDate".equals(scope)) {
                    return "現在日時";
                }
            }
        }

        // DriverManager.getConnection(dburl) -> DriverManager から dburl で接続取得
        if ("getConnection".equals(methodName) && mc.getArguments().size() == 1) {
            if (mc.getScope().isPresent() && "DriverManager".equals(mc.getScope().get().toString())) {
                String scope = mc.getScope().get().toString();
                String arg = convertExpressionToString(mc.getArgument(0));
                return scope + " から " + arg + " で接続取得";
            }
        }

        // u.openStream() -> u からの通り道
        if ("openStream".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + " からの通り道";
        }

        // res.getWriter() -> res の 書き込み設定
        if ("getWriter".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + " の 書き込み設定";
        }

        // res.setContentType("text/html") -> res の 形式を 「text/html」に設定
        if ("setContentType".equals(methodName) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            String arg = convertArgument(mc.getArgument(0));
            return scope + " の 形式を " + arg + "に設定";
        }


        // ZonedDateTime.of(y, m, d, h, min, s, n, zone)
        if ("of".equals(methodName) && mc.getArguments().size() == 8) {
            if (mc.getScope().isPresent() && "ZonedDateTime".equals(mc.getScope().get().toString())) {
                String zoneIdStr = getZoneIdString(mc.getArgument(7));
                String zoneJp = convertZoneIdToJapanese(zoneIdStr);
                String y = convertExpressionToString(mc.getArgument(0));
                String m = convertExpressionToString(mc.getArgument(1));
                String d = convertExpressionToString(mc.getArgument(2));
                String h = convertExpressionToString(mc.getArgument(3));
                String min = convertExpressionToString(mc.getArgument(4));
                String s = convertExpressionToString(mc.getArgument(5));
                String n = convertExpressionToString(mc.getArgument(6));
                return String.format("%s時間%s年%s月%s日%s時%s分%s秒%sナノ秒", zoneJp, y, m, d, h, min, s, n);
            }
        }

        // LocalDateTime.of(...)
        if ("of".equals(methodName) && mc.getScope().isPresent() && "LocalDateTime".equals(mc.getScope().get().toString())) {
            List<String> args = mc.getArguments().stream()
                    .map(MethodConverter::convertExpressionToString)
                    .collect(Collectors.toList());
            if (args.size() >= 5) { // year, month, day, hour, minute
                String datePart = String.format("%s年%s月%s日", args.get(0), args.get(1), args.get(2));
                String timePart = "";
                if (args.size() == 5) {
                    timePart = String.format("%s時%s分", args.get(3), args.get(4));
                } else if (args.size() == 6) {
                    timePart = String.format("%s時%s分%s秒", args.get(3), args.get(4), args.get(5));
                } else if (args.size() >= 7) {
                    timePart = String.format("%s時%s分%s秒", args.get(3), args.get(4), args.get(5));
                    // ナノ秒は出力に含めない
                }
                return datePart + timePart;
            }
        }

        // LocalDate.of(y, m, d)
        if ("of".equals(methodName) && mc.getScope().isPresent() && "LocalDate".equals(mc.getScope().get().toString())) {
            List<String> args = mc.getArguments().stream()
                    .map(MethodConverter::convertExpressionToString)
                    .collect(Collectors.toList());
            if (args.size() == 3) { // year, month, day
                return String.format("%s年%s月%s日", args.get(0), args.get(1), args.get(2));
            }
        }


        // zonedDateTime.toInstant()
        if ("toInstant".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "の時間";
        }

        // instant.atZone(zone)
        if ("toLocalDateTime".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "の日時";
        }

        // instant.atZone(zone)
        if ("atZone".equals(methodName) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            String zoneJp = convertZoneIdToJapanese(getZoneIdString(mc.getArgument(0)));
            return scope + "を「" + zoneJp + "」に地域変換";
        }

        // ZonedDateTime.getYear(), getMonthValue(), etc.
        if (methodName.startsWith("get") && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String property = getZonedDateTimePropertyName(methodName);
            if (property != null) {
                String scope = convertExpressionToString(mc.getScope().get());
                return scope + "の" + property;
            }
        }

        // Instant.now()
        if ("now".equals(methodName) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent() && "Instant".equals(mc.getScope().get().toString())) {
                return "現在日時";
            }
        }

        // Instant.ofEpochMilli(long)
        if ("ofEpochMilli".equals(methodName) && mc.getArguments().size() == 1) {
            if (mc.getScope().isPresent() && "Instant".equals(mc.getScope().get().toString())) {
                String arg = ExpressionConverter.getValueString(mc.getArgument(0));
                return (arg != null ? arg : mc.getArgument(0).toString()) + "のシステム時間";
            }
        }

        // instant.toEpochMilli()
        if ("toEpochMilli".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "の日時";
        }

        // Date.getTime() or Calendar.getTime()
        if ("getTime".equals(methodName) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent()) {
                String scope = convertExpressionToString(mc.getScope().get());
                return scope + "のシステム時間";
            }
            return "システム時間";
        }

        // Calendar.getInstance()
        if ("getInstance".equals(methodName) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent() && "Calendar".equals(mc.getScope().get().toString())) {
                return "カレンダー生成";
            }
        }

        // Collection.get(key) or List.get(index)
        if ("get".equals(methodName) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            Expression argument = mc.getArgument(0);
            String key = convertExpressionToString(argument);

            // 引数が数値リテラルならList.get(index)とみなし、「n番目」と変換
            if (argument.isIntegerLiteralExpr()) {
                return scope + "の" + key + "番目";
            } else {
                // 引数が文字列リテラルなどであればMap.get(key)とみなし、「keyのペア」と変換
                return scope + "の" + key + "のペア";
            }
        }

        // Calendar.get(field)
        if ("get".equals(methodName) && mc.getArguments().size() == 1) {
            if (mc.getScope().isPresent()) {
                Expression field = mc.getArgument(0);
                if (field instanceof FieldAccessExpr) {
                    String fieldName = ((FieldAccessExpr) field).getNameAsString();
                    String fieldJp = getCalendarFieldName(fieldName);
                    if (fieldJp != null) {
                        String scope = convertExpressionToString(mc.getScope().get());
                        return scope + "の" + fieldJp;
                    }
                }
            }
        }

        // Period.ofDays(3) -> 3日の期間
        if (("ofDays".equals(methodName) || "ofMonths".equals(methodName) || "ofYears".equals(methodName)) && mc.getArguments().size() == 1) {
            if (mc.getScope().isPresent() && "Period".equals(mc.getScope().get().toString())) {
                String value = convertExpressionToString(mc.getArgument(0));
                String unit = "";
                if ("ofDays".equals(methodName)) unit = "日";
                if ("ofMonths".equals(methodName)) unit = "ヶ月";
                if ("ofYears".equals(methodName)) unit = "年";
                return value + unit + "の期間";
            }
        }

        // Period.between(d1, d2) -> d1からd2までの期間
        if ("between".equals(methodName) && mc.getArguments().size() == 2) {
            if (mc.getScope().isPresent() && "Period".equals(mc.getScope().get().toString())) {
                String start = convertExpressionToString(mc.getArgument(0));
                String end = convertExpressionToString(mc.getArgument(1));
                return start + "から" + end + "までの期間";
            }
        }


        // DateTimeFormatter.ofPattern("...")
        if ("ofPattern".equals(methodName) && mc.getArguments().size() == 1) {
            if (mc.getScope().isPresent() && "DateTimeFormatter".equals(mc.getScope().get().toString())) {
                String pattern = convertExpressionToString(mc.getArgument(0));
                return "日時フォーマット(" + pattern + ")作成";
            }
        }

        // LocalDate.parse(text, formatter)
        if ("parse".equals(methodName) && mc.getArguments().size() == 2) {
            if (mc.getScope().isPresent() && "LocalDate".equals(mc.getScope().get().toString())) {
                String dateString = convertExpressionToString(mc.getArgument(0));
                String formatter = convertExpressionToString(mc.getArgument(1));
                return dateString + "を" + formatter + "の形式逆変換";
            }
        }

        // ldate.plusDays(1000) -> ldateに1000日加算
        if ((methodName.startsWith("plus") || methodName.startsWith("minus")) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            String dateTimeConversion = convertDateTimePlusMinus(mc);
            if (dateTimeConversion != null) {
                return dateTimeConversion;
            }
        }

        // ldatep.format(fmt) -> ldatepをfmtの形式変換
        if ("format".equals(methodName) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            String formatter = convertExpressionToString(mc.getArgument(0));
            return scope + "を" + formatter + "の形式変換";
        }

        // names.iterator() -> namesのイテレータ
        if ("iterator".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "のイテレータ";
        }

        // it.next() -> itの次の要素
        if ("next".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            // To avoid conflict with Scanner's next(), we can check the scope type if needed.
            // For now, this general rule should work for iterators.
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "の次の要素";
        }

        // collection.size() -> collectionの要素数
        if ("size".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "の要素数";
        }

        // map.keySet() -> mapのキー一覧
        if ("keySet".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "のキー一覧";
        }

        // Setter: p.setAge(-128) -> pの年齢に-128を設定
        if (methodName.startsWith("set") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3)) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            String scope = convertExpressionToString(mc.getScope().get());
            String propertyName = methodName.substring(3);
            // 簡単な英和辞書でプロパティ名を変換
            String propertyNameJp = propertyName; // 変換できない場合はそのまま
            String value = convertExpressionToString(mc.getArgument(0));
            return scope + "の" + propertyNameJp + "に" + value + "を設定";
        }

        // DateFormat.parse(String)
        if ("parse".equals(methodName) && mc.getArguments().size() == 1) {
            if (mc.getScope().isPresent()) {
                String scope = convertExpressionToString(mc.getScope().get());
                String dateString = convertExpressionToString(mc.getArgument(0));
                return dateString + "を" + scope + "の形式逆変換";
            }
        }

        // DateFormat.format(Date)
        if ("format".equals(methodName) && mc.getArguments().size() == 1 && mc.getScope().isPresent()) {
            // このブロックは新しい `format` の実装に置き換えられたため、
            // String.format との衝突を避けるための古いロジックは不要になります。
            // ただし、古い DateFormat のためのロジックがまだ必要であれば、
            // スコープの型をチェックして分岐させることができます。
            // if (mc.getScope().get().calculateResolvedType().asReferenceType().getQualifiedName().endsWith("DateFormat")) { ... }
            String scope = convertExpressionToString(mc.getScope().get());
            String dateObject = convertExpressionToString(mc.getArgument(0));
            return dateObject + "を" + scope + "の形式変換";
        }

        // Math.max, Math.min の特別処理
        if (mc.getScope().isPresent() && "Math".equals(mc.getScope().get().toString())) {
            if (("max".equals(methodName) || "min".equals(methodName)) && mc.getArguments().size() == 2) {
                String arg1 = convertExpressionToString(mc.getArgument(0));
                String arg2 = convertExpressionToString(mc.getArgument(1));
                String operation = "max".equals(methodName) ? "最大値" : "最小値";
                return arg1 + "と" + arg2 + "の" + operation;
            }
        }

        // Integer.parseInt の特別処理
        if (mc.getScope().isPresent() && "Integer".equals(mc.getScope().get().toString())) {
            if ("parseInt".equals(methodName) && mc.getArguments().size() == 1) {
                String arg = convertExpressionToString(mc.getArgument(0));
                return arg + "を整数変換";
            }
        }

        // Random().nextInt(n) の特別処理
        if ("nextInt".equals(methodName) && mc.getArguments().size() == 1) {
            if (mc.getScope().isPresent()) {
                Expression scope = mc.getScope().get();
                // new Random().nextInt(n) のような形式を検出
                if (scope.isObjectCreationExpr() && scope.asObjectCreationExpr().getType().getNameAsString().contains("Random")) {
                    String arg = convertExpressionToString(mc.getArgument(0));
                    return arg + "の乱数";
                }
            }
        }

        // new Scanner(System.in).next...() の特別処理
        if (("nextLine".equals(methodName) || "nextInt".equals(methodName) || "nextDouble".equals(methodName)) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent()) {
                Expression scope = mc.getScope().get();
                // new Scanner(System.in) のような形式を検出
                if (scope.isObjectCreationExpr()) {
                    ObjectCreationExpr oce = scope.asObjectCreationExpr();
                    if (oce.getType().getNameAsString().contains("Scanner") &&
                        oce.getArguments().size() == 1 &&
                        "System.in".equals(oce.getArgument(0).toString())) {
                        
                        if ("nextLine".equals(methodName)) return "文字列読み込み";
                        if ("nextInt".equals(methodName)) return "整数読み込み";
                        if ("nextDouble".equals(methodName)) return "少数読み込み";
                    }
                }
            }
        }

        // String.length()
        if ("length".equals(methodName) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent()) {
                String scope = convertExpressionToString(mc.getScope().get());
                return scope + "の長さ";
            }
            return "長さ"; // スコープがない場合は稀だが念のため
        }

        // toString()
        if ("toString".equals(methodName) && mc.getArguments().isEmpty()) {
            if (mc.getScope().isPresent()) {
                String scope = convertExpressionToString(mc.getScope().get());
                return scope + "の文字列変換";
            }
            return "文字列変換"; // スコープがない場合は稀だが念のため
        }

        // e.printStackTrace() -> エラー詳細出力
        if ("printStackTrace".equals(methodName) && mc.getArguments().isEmpty()) {
            // スコープ(e)は出力に含めない
            return "エラー詳細出力";
        }

        // fr.read() -> frから1文字読込
        if ("read".equals(methodName) && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            // To be more precise, we could check if the scope is a type of Reader.
            // For now, we assume `read()` without arguments is for reading a single character.
            String scope = convertExpressionToString(mc.getScope().get());
            return scope + "から1文字読込";
        }


        // matches
        if ("matches".equals(methodName) && mc.getArguments().size() == 1) {
            String left = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            String pattern = mc.getArgument(0).isStringLiteralExpr()
                    ? "「" + mc.getArgument(0).asStringLiteralExpr().asString() + "」"
                    : mc.getArgument(0).toString();
            return left + "を" + pattern + "で正規表現マッチ";
        }

        // equals
        if ("equals".equals(methodName) && mc.getArguments().size() == 1) {
            String left = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            String right = convertExpressionToString(mc.getArgument(0));
            return left + "が" + right + "と等しい";
        }

        // StringBuilder.append()
        if ("append".equals(methodName) && mc.getArguments().size() == 1) {
            String scope = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            String arg = convertArgument(mc.getArgument(0));
            // スコープが空でない場合のみ「に」を追加
            return scope + (scope.isEmpty() ? "" : "に") + arg + "追加";
        }

        // String.replaceAll(regex, replacement)
        if ("replaceAll".equals(methodName) && mc.getArguments().size() == 2) {
            String scope = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            String regex = convertExpressionToString(mc.getArgument(0));
            String replacement = convertExpressionToString(mc.getArgument(1));

            return scope + "の" + regex + "を" + replacement + "へ正規表現置換";
        }

        // String.split(regex)
        if ("split".equals(methodName) && mc.getArguments().size() == 1) {
            String scope = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            String delimiter = mc.getArgument(0).isStringLiteralExpr()
                    ? "「" + mc.getArgument(0).asStringLiteralExpr().getValue() + "」"
                    : convertExpressionToString(mc.getArgument(0));
            return scope + "を" + delimiter + "で正規表現区切る";
        }

        // String.format(format, args...)
        if ("format".equals(methodName) && mc.getScope().isPresent() && "String".equals(mc.getScope().get().toString())) {
            if (mc.getArguments().size() >= 1) {
                String formatString = convertExpressionToString(mc.getArgument(0));
                String args = mc.getArguments().stream()
                        .skip(1) // Skip the format string argument
                        .map(arg -> {
                            // Here we use toString() to get a more literal representation of the arguments
                            return arg.toString();
                        })
                        .collect(Collectors.joining(", "));
                return formatString + "を「" + args + "」で形式指定";
            }
        }


        // substring
        if ("substring".equals(methodName)) {
            String scope = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            
            Expression startIndexExpr = mc.getArgument(0);
            String startIndexConverted;
            if (startIndexExpr.isIntegerLiteralExpr()) {
                try {
                    int startIndexValue = Integer.parseInt(startIndexExpr.asIntegerLiteralExpr().getValue());
                    startIndexConverted = String.valueOf(startIndexValue + 1);
                } catch (NumberFormatException e) {
                    startIndexConverted = convertExpressionToString(startIndexExpr) + "+1";
                }
            } else {
                startIndexConverted = convertExpressionToString(startIndexExpr) + "+1";
            }

            if (mc.getArguments().size() == 1) {
                // s1.substring(startIndex) -> s1の(startIndex+1)文字目以降の文字列
                return scope + "の" + startIndexConverted + "文字目以降の文字列";
            } else if (mc.getArguments().size() == 2) {
                // s1.substring(startIndex, endIndex) -> s1の(startIndex+1)~endIndex文字目の文字列
                Expression endIndexExpr = mc.getArgument(1);
                String endIndexConverted = convertExpressionToString(endIndexExpr);
                return scope + "の" + startIndexConverted + "~" + endIndexConverted + "文字目の文字列";
            }
        }

        // indexOf / lastIndexOf
        if (("indexOf".equals(methodName) || "lastIndexOf".equals(methodName)) && mc.getArguments().size() == 1) {
            String scope = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            String arg = convertExpressionToString(mc.getArgument(0));
            String positionType = "indexOf".equals(methodName) ? "最初" : "最後";

            // 引数が文字列リテラルの場合、二重引用符を削除して「」で囲む
            if (mc.getArgument(0).isStringLiteralExpr()) {
                arg = "「" + mc.getArgument(0).asStringLiteralExpr().getValue() + "」";
            }

            return scope + "で" + arg + "が" + positionType + "に出る位置";
        }

        // charAt(index)
        if ("charAt".equals(methodName) && mc.getArguments().size() == 1) {
            String scope = mc.getScope()
                    .map(MethodConverter::convertExpressionToString)
                    .orElse("");
            
            Expression indexExpr = mc.getArgument(0);
            String indexConverted;
            if (indexExpr.isIntegerLiteralExpr()) {
                try {
                    int indexValue = Integer.parseInt(indexExpr.asIntegerLiteralExpr().getValue());
                    indexConverted = String.valueOf(indexValue + 1);
                } catch (NumberFormatException e) {
                    indexConverted = convertExpressionToString(indexExpr) + "+1";
                }
            } else {
                indexConverted = convertExpressionToString(indexExpr) + "+1";
            }
            return scope + "の" + indexConverted + "文字目";
        }

        // デフォルト
        String scopePrefix = "";
        if (mc.getScope().isPresent()) {
            Expression scopeExpr = mc.getScope().get();
            if (scopeExpr.isThisExpr()) {
                scopePrefix = "自身の";
            } else if (scopeExpr.isSuperExpr()) {
                scopePrefix = "親の";
            } else {
                scopePrefix = convertExpressionToString(scopeExpr) + "の";
            }
        }
        String args = mc.getArguments().stream()
                .map(MethodConverter::convertExpressionToString)
                .collect(Collectors.joining(", "));

        return args.isEmpty()
                ? scopePrefix + methodName // 引数がない場合は()を付けない
                : scopePrefix + methodName + "(" + args + ")";
    }

    private static String convertDateTimePlusMinus(MethodCallExpr mc) {
        String methodName = mc.getNameAsString();
        String operation;
        String unit;

        // plus(Period), minus(Period)
        if (("plus".equals(methodName) || "minus".equals(methodName)) && mc.getArguments().size() == 1) {
            String scope = convertExpressionToString(mc.getScope().get());
            String period = convertExpressionToString(mc.getArgument(0));
            return scope + "に" + period + ("plus".equals(methodName) ? "加算" : "減算");
        }

        if (methodName.startsWith("plus")) {
            operation = "加算";
            unit = methodName.substring(4);
        } else if (methodName.startsWith("minus")) {
            operation = "減算";
            unit = methodName.substring(5);
        } else {
            return null;
        }

        String unitJp = switch (unit) {
            case "Days" -> "日";
            case "Weeks" -> "週間";
            case "Months" -> "ヶ月";
            case "Years" -> "年";
            case "Hours" -> "時間";
            case "Minutes" -> "分";
            case "Seconds" -> "秒";
            default -> null;
        };

        if (unitJp == null) return null;

        String scope = convertExpressionToString(mc.getScope().get());
        String value = convertExpressionToString(mc.getArgument(0));
        return scope + "に" + value + unitJp + operation;
    }

    /**
     * ZoneId.of("...") のような式からタイムゾーンID文字列を取得する
     */
    private static String getZoneIdString(Expression expr) {
        if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            if ("of".equals(call.getNameAsString()) && call.getArguments().size() == 1 && call.getScope().isPresent() && "ZoneId".equals(call.getScope().get().toString())) {
                Expression arg = call.getArgument(0);
                if (arg.isStringLiteralExpr()) {
                    return arg.asStringLiteralExpr().getValue();
                }
            }
        }
        return expr.toString(); // フォールバック
    }

    /**
     * タイムゾーンIDから日本語の地域名に変換する
     */
    private static String convertZoneIdToJapanese(String zoneId) {
        return switch (zoneId) {
            case "Asia/Tokyo" -> "東京";
            case "Europe/London" -> "ロンドン";
            default -> zoneId; // 不明な場合はそのまま返す
        };
    }

    /**
     * ZonedDateTime のゲッターメソッド名を日本語のプロパティ名に変換する
     */
    private static String getZonedDateTimePropertyName(String methodName) {
        return switch (methodName) {
            case "getYear" -> "年";
            case "getMonth" -> "月";
            case "getMonthValue" -> "月";
            case "getDayOfMonth" -> "日";
            case "getHour" -> "時";
            case "getMinute" -> "分";
            case "getSecond" -> "秒";
            case "getNano" -> "ナノ秒";
            default -> null;
        };
    }

    private static String getCalendarFieldName(String fieldName) {
        return switch (fieldName) {
            case "YEAR"-> "年";
            case "MONTH" -> "月";
            case "DAY_OF_MONTH", "DATE" -> "日";
            case "HOUR" -> "時"; // 12時間制
            case "HOUR_OF_DAY" -> "24時間";
            case "MINUTE" -> "分";
            case "SECOND" -> "秒";
            case "MILLISECOND" -> "ミリ秒";
            case "DAY_OF_WEEK" -> "曜日";
            case "DAY_OF_YEAR" -> "年間通算日";
            case "WEEK_OF_YEAR" -> "年間通算週";
            default -> null;
        };
    }

    /**
     * 変換結果を格納するデータクラス
     */
    public static class Item {
        public final int line;
        public final String content;
        public final int priority;

        public Item(int line, String content, int priority) {
            this.line = line;
            this.content = content;
            this.priority = priority;
        }
    }
}
