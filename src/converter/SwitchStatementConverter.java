package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class SwitchStatementConverter {
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(SwitchStmt switchStmt, Void arg) {
                int line = switchStmt.getBegin().map(p -> p.line).orElse(-1);
                String outerIndent = IndentManager.getIndentForLine(line);
                String switchVar = switchStmt.getSelector().toString();
                items.add(new Item(line, outerIndent + switchVar + "で条件分岐："));

                String caseIndent = outerIndent + "　";
                String statementIndent = caseIndent + "　";

                for (SwitchEntry entry : switchStmt.getEntries()) {
                    int entryLine = entry.getBegin().map(p -> p.line).orElse(-1);
                    if (entry.getLabels().isEmpty()) { // default
                        items.add(new Item(entryLine, caseIndent + "それ以外ならば："));
                    } else {
                        for (Expression label : entry.getLabels()) {
                            // ラベルごとに行を追加するのではなく、最初の一つのラベルの行にまとめる
                            // ただし、JavaParserの仕様上、複数のラベルが1つのSwitchEntryにまとまるため、
                            // ここでは各ラベルを別々の行として出力する
                            items.add(new Item(entryLine, caseIndent + label.toString() + "ならば："));
                        }
                    }

                    // caseブロック内のインデントを記録するロジックを統合
                    int lastLine = entry.getBegin().map(p -> p.line).orElse(0);
                    for (Statement stmt : entry.getStatements()) {
                        int startLine = stmt.getBegin().map(p -> p.line).orElse(-1);
                        if (startLine != -1) {
                            // 前の文の終わりから今の文の始まりまで(コメント行や空行)をインデント
                            for (int i = lastLine + 1; i < startLine; i++) {
                                IndentManager.recordIndentForLine(i, statementIndent);
                            }

                            // break文は特別に変換し、それ以外の文はインデントを記録
                            if (stmt instanceof BreakStmt) {
                                items.add(new Item(startLine, statementIndent + "抜ける。"));
                                lastLine = stmt.getEnd().map(p -> p.line).orElse(startLine);
                            } else if (stmt.isBlockStmt()) {
                                // case 0 -> { ... } のようなアロー構文のブロックを処理
                                MethodConverter.MethodVisitor.processBlock(stmt.asBlockStmt(), statementIndent);
                                // ブロック全体の行範囲を更新
                                lastLine = stmt.getEnd().map(p -> p.line).orElse(startLine);
                            } else {
                                IndentManager.recordIndentForLine(startLine, statementIndent);
                                lastLine = stmt.getEnd().map(p -> p.line).orElse(startLine);
                            }
                        }
                    }
                    // 最後の文からSwitchEntryの終わりまでのコメント/空行をインデント
                    int entryEndLine = entry.getEnd().map(p -> p.line).orElse(0);
                    for (int i = lastLine + 1; i < entryEndLine; i++) {
                        IndentManager.recordIndentForLine(i, statementIndent);
                    }
                }
                int endLine = switchStmt.getEnd().map(p -> p.line).orElse(-1);
                items.add(new Item(endLine, outerIndent + "ここまで。"));
                super.visit(switchStmt, arg);
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