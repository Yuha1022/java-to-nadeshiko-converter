package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class PackageConverter {
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(PackageDeclaration pkg, Void arg) {
                int line = pkg.getBegin().map(p -> p.line).orElse(-1);
                String packageName = pkg.getNameAsString();
                items.add(new Item(line, "「" + packageName + "」に所属。", 5));
                super.visit(pkg, arg);
            }
        }, null);
        
        return items;
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