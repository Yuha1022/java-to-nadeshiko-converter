package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class IfStatementConverter {

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(IfStmt ifStmt, Void arg) {
                // else ifの一部として処理される場合はスキップ
                if (isPartOfElseIf(ifStmt)) {
                    super.visit(ifStmt, arg);
                    return;
                }
                int line = ifStmt.getBegin().map(p -> p.line).orElse(-1);
                String condition = ConditionConverter.convertCondition(ifStmt.getCondition());
                String outerIndent = IndentManager.getIndentForLine(line);

                // then 節が単一の return を含む場合は if 行と return 行を分けて出力する
                Statement thenStmt = ifStmt.getThenStmt();
                if (isSingleReturn(thenStmt)) {
                    com.github.javaparser.ast.stmt.ReturnStmt returnStmt;
                    if (thenStmt instanceof BlockStmt) {
                        BlockStmt blk = (BlockStmt) thenStmt;
                        returnStmt = (com.github.javaparser.ast.stmt.ReturnStmt) blk.getStatements().get(0);
                    } else {
                        returnStmt = (com.github.javaparser.ast.stmt.ReturnStmt) thenStmt;
                    }

                    // 1) if 行
                    String ifText = outerIndent + "もし、(" + condition + ")ならば";
                    items.add(new Item(line, ifText, 5));

                    // 2) indented return 行(return の行番号を使う)
                    int returnLine = returnStmt.getBegin().map(p -> p.line).orElse(line + 1);
                    String returnValue = returnStmt.getExpression()
                            .map(e -> ConditionConverter.convertExpressionToString(e))
                            .orElse("");
                    String returnIndent = outerIndent + "　";
                    if (!returnValue.isEmpty()) {
                        items.add(new Item(returnLine, returnIndent + returnValue + "を戻す。", 35));
                    } else {
                        items.add(new Item(returnLine, returnIndent + "戻す。", 35));
                    }
                } else {
                    String ifText = outerIndent + "もし、(" + condition + ")ならば";
                    items.add(new Item(line, ifText, 5));
                    // then節がブロック文の場合はMethodConverterが処理する。
                    // そうでない単一文の場合は、ここでインデントを記録する。
                    if (!(thenStmt instanceof BlockStmt)) {
                        thenStmt.getBegin().ifPresent(p -> IndentManager.recordIndentForLine(p.line, outerIndent + "　"));
                    }
                }

                // else節の処理
                int endLine = ifStmt.getEnd().map(p -> p.line).orElse(-1);
                if (ifStmt.getElseStmt().isPresent()) {
                    Statement elseStmt = ifStmt.getElseStmt().get();
                    if (elseStmt instanceof IfStmt) { // else if
                        IfStmt elseIfStmt = (IfStmt) elseStmt;
                        processElseIfChain(elseIfStmt, outerIndent, items);
                    } else { // else
                        int elseLine = elseStmt.getBegin().map(p -> p.line).orElse(-1);
                        items.add(new Item(elseLine, outerIndent + "違えば", 5));
                        // else節がブロック文の場合はMethodConverterが処理する。
                        // そうでない単一文の場合は、ここでインデントを記録する。
                        if (!(elseStmt instanceof BlockStmt)) {
                            elseStmt.getBegin().ifPresent(p -> IndentManager.recordIndentForLine(p.line, outerIndent + "　"));
                        }
                    }
                }
                
                // if文全体の終了を示す「ここまで。」を追加
                // priorityを999にして確実に最後に配置
                items.add(new Item(endLine, outerIndent + "ここまで。", 999));
                
                super.visit(ifStmt, arg);
            }
        }, null);
        return items;
    }

    private static void processElseIfChain(IfStmt elseIfStmt, String outerIndent, List<Item> items) {
        int elseLine = elseIfStmt.getBegin().map(p -> p.line).orElse(-1);
        String elseIfCondition = ConditionConverter.convertCondition(elseIfStmt.getCondition());

        // else-if の then 節も single-return の場合は if 行と return 行を分けて出力
        Statement thenStmt = elseIfStmt.getThenStmt();
        if (isSingleReturn(thenStmt)) {
            com.github.javaparser.ast.stmt.ReturnStmt returnStmt;
            if (thenStmt instanceof BlockStmt) {
                BlockStmt blk = (BlockStmt) thenStmt;
                returnStmt = (com.github.javaparser.ast.stmt.ReturnStmt) blk.getStatements().get(0);
            } else {
                returnStmt = (com.github.javaparser.ast.stmt.ReturnStmt) thenStmt;
            }

            // else-if の if 行
            items.add(new Item(elseLine, outerIndent + "違えば、もし、(" + elseIfCondition + ")ならば", 5));

            // return 行(インデントを付ける)
            int returnLine = returnStmt.getBegin().map(p -> p.line).orElse(elseLine + 1);
            String returnValue = returnStmt.getExpression()
                    .map(e -> ConditionConverter.convertExpressionToString(e))
                    .orElse("");
            String returnIndent = outerIndent + "　";
            if (!returnValue.isEmpty()) {
                items.add(new Item(returnLine, returnIndent + returnValue + "を戻す。", 35));
            } else {
                items.add(new Item(returnLine, returnIndent + "戻す。", 35));
            }
        } else {
            items.add(new Item(elseLine, outerIndent + "違えば、もし、(" + elseIfCondition + ")ならば", 5));
            // else-ifのthen節がブロック文の場合はMethodConverterが処理する。
            // そうでない単一文の場合は、ここでインデントを記録する。
            if (thenStmt instanceof BlockStmt) {
                MethodConverter.MethodVisitor.processBlock((BlockStmt) thenStmt, outerIndent + "　");
            }
            if (!(thenStmt instanceof BlockStmt)) {
                thenStmt.getBegin().ifPresent(p -> IndentManager.recordIndentForLine(p.line, outerIndent + "　"));
            }
        }

        if (elseIfStmt.getElseStmt().isPresent()) {
            Statement elseStmt = elseIfStmt.getElseStmt().get();
            int nextElseLine = elseStmt.getBegin().map(p -> p.line).orElse(-1);
            if (elseStmt instanceof IfStmt) {
                processElseIfChain((IfStmt) elseStmt, outerIndent, items);
            } else {
                items.add(new Item(nextElseLine, outerIndent + "違えば", 5));
                // 最後のelse節がブロック文の場合はMethodConverterが処理する。
                if (elseStmt instanceof BlockStmt) {
                    MethodConverter.MethodVisitor.processBlock((BlockStmt) elseStmt, outerIndent + "　");
                }
                if (!(elseStmt instanceof BlockStmt)) {
                    elseStmt.getBegin().ifPresent(p -> IndentManager.recordIndentForLine(p.line, outerIndent + "　"));
                }
            }
        }
    }

    private static boolean isSingleReturn(Statement stmt) {
        if (stmt instanceof com.github.javaparser.ast.stmt.ReturnStmt) {
            return true;
        }
        if (stmt instanceof BlockStmt) {
            BlockStmt blk = (BlockStmt) stmt;
            if (blk.getStatements().size() == 1 && blk.getStatement(0) instanceof com.github.javaparser.ast.stmt.ReturnStmt) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPartOfElseIf(IfStmt ifStmt) {
        if (ifStmt.getParentNode().isPresent()) {
            Node parent = ifStmt.getParentNode().get();
            if (parent instanceof IfStmt) {
                IfStmt parentIf = (IfStmt) parent;
                if (parentIf.getElseStmt().isPresent() && parentIf.getElseStmt().get() == ifStmt) {
                    return true;
                }
            }
        }
        return false;
    }

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