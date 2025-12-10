package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class VariableInitConverter {
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarator variable, Void arg) {
                if (variable.getInitializer().isPresent()) {
                    int line = variable.getBegin().map(p -> p.line).orElse(-1);
                    // for文の初期化式の場合はスキップ
                    if (isForInitialization(variable)) {
                        super.visit(variable, arg);
                        return;
                    }

                    // try-with-resources のリソース宣言は TryCatchConverter で処理するのでスキップ
                    if (variable.getParentNode().flatMap(Node::getParentNode).map(p -> p instanceof TryStmt).orElse(false)) {
                        super.visit(variable, arg);
                        return;
                    }

                    Expression initializer = variable.getInitializer().get();
                    if (initializer instanceof ArrayCreationExpr || initializer instanceof ArrayInitializerExpr) {
                        super.visit(variable, arg);
                        return;
                    }

                    String variableName = variable.getNameAsString();
                    boolean isFinal = checkIsFinal(variable);
                    String text = convertInitializer(variableName, initializer, isFinal);
                    if (text != null) {
                        String indent = IndentManager.getIndentForLine(line);
                        items.add(new Item(line, indent + text));
                    }
                }
                super.visit(variable, arg);
            }

            @Override
            public void visit(AssignExpr assignExpr, Void arg) {
                int line = assignExpr.getBegin().map(p -> p.line).orElse(-1);

                // 変数宣言の一部である代入式は、VariableDeclaratorのvisitで処理されるため、ここではスキップ
                if (assignExpr.getParentNode().isPresent() &&
                    (assignExpr.getParentNode().get() instanceof VariableDeclarator ||
                     assignExpr.getParentNode().get() instanceof EnclosedExpr)) {
                    super.visit(assignExpr, arg);
                    return;
                }                
                // for文の更新式の場合はスキップ
                if (isForUpdate(assignExpr)) {
                    super.visit(assignExpr, arg);
                    return;
                }

                String variableName = convertTargetExpression(assignExpr.getTarget());
                Expression value = assignExpr.getValue();
                String text = null;

                if (assignExpr.getOperator() == AssignExpr.Operator.ASSIGN) {
                    // for文の初期化式の場合はスキップ
                    if (isForInitialization(assignExpr)) {
                        super.visit(assignExpr, arg);
                        return;
                    }
                    if (value instanceof ArrayCreationExpr || value instanceof ArrayInitializerExpr) {
                        super.visit(assignExpr, arg);
                        return;
                    }
                    text = convertInitializer(variableName, value, false);
                } else { // 複合代入演算子の場合
                    text = convertInitializer(variableName, assignExpr, false);
                }

                if (text != null) {
                    String indent = IndentManager.getIndentForLine(line);
                    items.add(new Item(line, indent + text));
                }
                super.visit(assignExpr, arg);
            }

            @Override
            public void visit(UnaryExpr unaryExpr, Void arg) {
                int line = unaryExpr.getBegin().map(p -> p.line).orElse(-1);

                // for文の更新式の場合はスキップ
                if (isForUpdate(unaryExpr)) {
                    super.visit(unaryExpr, arg);
                    return;
                }

                if (unaryExpr.getParentNode().isPresent()) {
                    var parent = unaryExpr.getParentNode().get();
                    // 式の一部である場合は、その式のコンバータで処理されるためスキップ
                    if (parent instanceof BinaryExpr || parent instanceof MethodCallExpr || parent instanceof AssignExpr
                            || parent instanceof IfStmt) {
                        super.visit(unaryExpr, arg);
                        return;
                    }
                }

                // ExpressionConverterから "a + 1" のような式部分を取得
                String expressionPart = ExpressionConverter.convertExpression(unaryExpr);
                String text = unaryExpr.getExpression().toString() + " は " + expressionPart;
                if (text != null) {
                    String indent = IndentManager.getIndentForLine(line);
                    items.add(new Item(line, indent + text + "。"));
                }
                super.visit(unaryExpr, arg);
            }
        }, null);
        return items;
    }

    private static String convertTargetExpression(Expression target) {
        if (target instanceof com.github.javaparser.ast.expr.FieldAccessExpr) {
            var fae = (com.github.javaparser.ast.expr.FieldAccessExpr) target;
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

    /**
     * 指定された変数がfor文の初期化部分に含まれるか判定する
     */
    private static boolean isForInitialization(VariableDeclarator variable) {
        return variable.getParentNode()
            .flatMap(Node::getParentNode)
            .map(grandParent -> {
                if (grandParent instanceof ForStmt) {
                    ForStmt forStmt = (ForStmt) grandParent;
                    // variable.getParentNode() は VariableDeclarationExpr
                    return forStmt.getInitialization().contains(variable.getParentNode().get());
                }
                return false;
            }).orElse(false);
    }

    /**
     * 指定された代入式がfor文の初期化部分に含まれるか判定する
     */
    private static boolean isForInitialization(AssignExpr expression) {
        return expression.getParentNode()
            .map(parent -> {
                if (parent instanceof ForStmt) {
                    ForStmt forStmt = (ForStmt) parent;
                    return forStmt.getInitialization().contains(expression);
                }
                // EnclosedExpr ((i=0)) のようなケースを考慮
                if (parent instanceof EnclosedExpr) {
                    return parent.getParentNode().map(grandParent ->
                        grandParent instanceof ForStmt && ((ForStmt) grandParent).getInitialization().contains(parent)
                    ).orElse(false);
                }
                return false;
            }).orElse(false);
    }

    /**
     * 指定された式がfor文の更新部分に含まれるか判定する
     */
    private static boolean isForUpdate(Expression expression) {
        return expression.getParentNode()
            .map(parent -> {
                if (parent instanceof ForStmt) {
                    ForStmt forStmt = (ForStmt) parent;
                    return forStmt.getUpdate().contains(expression);
                }
                // EnclosedExpr ((i++)) のようなケースを考慮
                if (parent instanceof EnclosedExpr) {
                    return parent.getParentNode().map(grandParent ->
                        grandParent instanceof ForStmt && ((ForStmt) grandParent).getUpdate().contains(parent)
                    ).orElse(false);
                }
                return false;
            }).orElse(false);
    }

    // CastExpr / EnclosedExpr を剥がすユーティリティ
    private static Expression unwrapExpression(Expression expr) {
        Expression cur = expr;
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

    private static boolean checkIsFinal(VariableDeclarator variable) {
        if (variable.getParentNode().isPresent()) {
            var parent = variable.getParentNode().get();
            if (parent instanceof VariableDeclarationExpr) {
                return ((VariableDeclarationExpr) parent).hasModifier(Modifier.Keyword.FINAL);
            } else if (parent instanceof FieldDeclaration) {
                return ((FieldDeclaration) parent).hasModifier(Modifier.Keyword.FINAL);
            }
        }
        return false;
    }

    public static String convertInitializer(String variableName, Expression expression, boolean isFinal) {
        // 右辺の変換をExpressionConverterに委譲
        String convertedExpr = ExpressionConverter.convertExpression(expression);
        if (convertedExpr != null) {
            if (isFinal) {
                return variableName + "は" + convertedExpr + "と定める。";
            } else {
                return variableName + " は " + convertedExpr + "。";
            }
        }
        return null;
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