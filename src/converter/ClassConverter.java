package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public class ClassConverter {

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

    public static List<Item> convert(CompilationUnit cu) {
        List<Item> out = new ArrayList<>();

        // 全てのクラス/インターフェイス宣言を検出
        List<TypeDeclaration<?>> types = cu.getTypes();

        for (TypeDeclaration<?> type : types) {
            if (type.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cls = type.asClassOrInterfaceDeclaration();

                // アノテーションの処理
                for (AnnotationExpr annotation : cls.getAnnotations()) {
                    int annotationLine = annotation.getBegin().map(p -> p.line).orElse(-1);
                    String annotationName = annotation.getNameAsString();
                    if ("RestController".equals(annotationName)) {
                        String indent = IndentManager.getIndentForLine(annotationLine);
                        // クラス宣言(priority=20)より先に表示するためpriorityを15に設定
                        out.add(new Item(annotationLine, indent + "Web応答用。", 15));
                    }
                }
                
                // 親をたどってインデントレベルを計算する
                int indentLevel = 0;
                java.util.Optional<com.github.javaparser.ast.Node> current = cls.getParentNode();
                while(current.isPresent() && !(current.get() instanceof CompilationUnit)) {
                    if (current.get() instanceof ClassOrInterfaceDeclaration) indentLevel++;
                    current = current.get().getParentNode();
                }
                String classIndent = "　".repeat(indentLevel);


                String name = cls.getNameAsString();

                // JavaParser から宣言の開始行を取得
                int startLine = cls.getBegin().map(p -> p.line).orElse(-1);
                
                // JavaParser から宣言の終了行を取得
                int endLine = cls.getEnd().map(p -> p.line).orElse(-1);
                
                // 変換ルール
                String topContent;
                if (cls.isInterface()) {
                    // インターフェースの場合
                    if (!cls.getExtendedTypes().isEmpty()) {
                        // 他のインターフェースを継承している場合
                        ClassOrInterfaceType extendedType = cls.getExtendedTypes().get(0);
                        String parentInterfaceName = extendedType.getNameAsString();
                        topContent = classIndent + "抽象クラス " + name + "は " + parentInterfaceName + "を継承";
                    } else {
                        // 継承していない場合
                        topContent = classIndent + "抽象クラス " + name;
                    }
                } else {
                    // クラスの場合
                    boolean hasExtends = !cls.getExtendedTypes().isEmpty();
                    boolean hasImplements = !cls.getImplementedTypes().isEmpty();
                    
                    if (hasExtends && hasImplements) {
                        // 継承と実装の両方がある場合
                        ClassOrInterfaceType extendedType = cls.getExtendedTypes().get(0);
                        String parentClassName = extendedType.getNameAsString();
                        ClassOrInterfaceType implementedType = cls.getImplementedTypes().get(0);
                        String interfaceName = implementedType.getNameAsString();
                        topContent = classIndent + "クラス " + name + "は " + parentClassName + "を継承、" + interfaceName + "を実装";
                    } else if (hasImplements) {
                        // 実装のみの場合
                        ClassOrInterfaceType implementedType = cls.getImplementedTypes().get(0);
                        String interfaceName = implementedType.getNameAsString();
                        topContent = classIndent + "クラス " + name + "は " + interfaceName + "を実装";
                    } else if (hasExtends) {
                        // 継承のみの場合
                        ClassOrInterfaceType extendedType = cls.getExtendedTypes().get(0);
                        String parentClassName = extendedType.getNameAsString();
                        topContent = classIndent + "クラス " + name + "は " + parentClassName + "を継承";
                    } else {
                        // 継承も実装もない場合
                        topContent = classIndent + "クラス " + name;
                    }
                }
                
                String bottomContent = classIndent + "ここまで。";
                
                final int START_PRIORITY = 20; 
                final int END_PRIORITY = 21;

                if (startLine != -1) {
                    out.add(new Item(startLine, topContent, START_PRIORITY));
                    // クラス本体のインデントを記録
                    // メンバーの有無に関わらず、クラスの `{` から `}` の間のすべての行にインデントを適用する
                    String bodyIndent = classIndent + "　";
                    // getLeftBrace/getRightBrace は古いJavaParserに存在しないため代替ロジックを使用
                    int bodyStartLine = cls.getMembers().stream()
                        .mapToInt(m -> m.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE))
                        .min()
                        .orElse(endLine);
                    // 最初のメンバーの行、またはメンバーがなければクラスの終了行を基準にする
                    // これにより、クラス宣言行の直後からインデントが開始される
                    bodyStartLine = Math.min(bodyStartLine, endLine);

                    // クラス本体の開始行の次から終了行の前までをインデント
                    for (int i = startLine + 1; i < endLine; i++) {
                        IndentManager.recordIndentForLine(i, bodyIndent);
                    }
                }

                if (endLine != -1) {
                    out.add(new Item(endLine, bottomContent, END_PRIORITY));
                }
            }
        }
        return out;
    }
}