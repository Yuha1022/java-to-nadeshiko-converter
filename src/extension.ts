import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';

export function activate(context: vscode.ExtensionContext) {
    const provider = new JavaToNadeshikoViewProvider();

    context.subscriptions.push(
        vscode.window.registerWebviewViewProvider('javaToNadeshikoView', provider)
    );

    context.subscriptions.push(
        vscode.window.onDidChangeActiveTextEditor(() => provider.updateContent())
    );

    // デバウンス付きでドキュメント変更を監視
    context.subscriptions.push(
        vscode.workspace.onDidChangeTextDocument((event) => {
            const activeEditor = vscode.window.activeTextEditor;
            if (activeEditor?.document === event.document && event.document.languageId === 'java') {
                provider.scheduleUpdate(); // デバウンス処理を使用
            }
        })
    );
}

class JavaToNadeshikoViewProvider implements vscode.WebviewViewProvider {
    private _view?: vscode.WebviewView;
    private _updateTimeout?: NodeJS.Timeout; // デバウンス用タイマー
    private _debounceDelay: number = 1000; // 1秒待機（調整可能）
    private _autoUpdate: boolean = true; // 自動更新のON/OFF

    resolveWebviewView(webviewView: vscode.WebviewView) {
        this._view = webviewView;
        webviewView.webview.options = { enableScripts: true };
        webviewView.webview.html = this.getHtml();

        // ビューからのメッセージを受信（手動更新ボタン用）
        webviewView.webview.onDidReceiveMessage(message => {
            if (message.type === 'manualUpdate') {
                this.updateContent();
            } else if (message.type === 'toggleAutoUpdate') {
                this._autoUpdate = message.value;
            }
        });

        this.updateContent();
    }

    // デバウンス処理：連続した呼び出しを遅延させる
    scheduleUpdate() {
        if (!this._autoUpdate) {
            return; // 自動更新が無効の場合は何もしない
        }

        // 既存のタイマーをキャンセル
        if (this._updateTimeout) {
            clearTimeout(this._updateTimeout);
        }

        // 新しいタイマーを設定
        this._updateTimeout = setTimeout(() => {
            this.updateContent();
        }, this._debounceDelay);
    }

    updateContent() {
        if (!this._view) {
            return;
        }

        const activeEditor = vscode.window.activeTextEditor;
        if (!activeEditor || activeEditor.document.languageId !== 'java') {
            this._view.webview.postMessage({
                type: 'update',
                content: 'Javaファイルを開いてください'
            });
            return;
        }

        // 変換中メッセージを表示
        this._view.webview.postMessage({
            type: 'update',
            content: '変換中...'
        });

        const javaCode = activeEditor.document.getText();
        const nadeshikoCode = this.convertJavaToNadeshiko(javaCode);
        this._view.webview.postMessage({
            type: 'update',
            content: nadeshikoCode
        });
    }

    private convertJavaToNadeshiko(javaCode: string): string {
        const baseDir = '/Users/yuha-kut/java-to-nadeshiko-converter/src';
        const jarFile = 'javaparser-core-3.26.2.jar';
        const tempFilePath = path.join(baseDir, 'temp_java_code.txt');

        try {
            fs.writeFileSync(tempFilePath, javaCode, 'utf8');

            // すべてのJavaファイルを再帰的に検索
            const javaFiles: string[] = [];
            const findJavaFiles = (dir: string) => {
                const entries = fs.readdirSync(dir, { withFileTypes: true });
                for (const entry of entries) {
                    const fullPath = path.join(dir, entry.name);
                    if (entry.isDirectory()) {
                        findJavaFiles(fullPath);
                    } else if (entry.isFile() && entry.name.endsWith('.java')) {
                        // 相対パスを取得（baseDirからの相対パス）
                        const relativePath = path.relative(baseDir, fullPath);
                        javaFiles.push(relativePath);
                    }
                }
            };
            findJavaFiles(baseDir);

            // すべてのJavaファイルをコンパイル（パスにスペースが含まれる場合に備えてクォート）
            const javaFilesQuoted = javaFiles.map(f => `"${f}"`).join(' ');
            execSync(
                `javac -cp ".:${jarFile}" ${javaFilesQuoted}`,
                { cwd: baseDir, encoding: 'utf8' }
            );

            const output = execSync(
                `java -cp ".:${jarFile}" JavaToNadeshikoConverter "${tempFilePath}"`,
                { cwd: baseDir, encoding: 'utf8' }
            );

            fs.unlinkSync(tempFilePath);
            return output.trim() || ' ';
        } catch (err: any) {
            return `変換エラー: ${err.message || err}`;
        }
    }

    getHtml(): string {
        return `<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>Java→なでしこ変換</title>
    <style>
        body { 
            padding: 10px; 
            font-family: var(--vscode-font-family);
        }
        #controls {
            margin-bottom: 10px;
            display: flex;
            gap: 10px;
            align-items: center;
        }
        button {
            padding: 5px 10px;
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            cursor: pointer;
        }
        button:hover {
            background: var(--vscode-button-hoverBackground);
        }
        #result {
            white-space: pre-wrap;
            font-family: var(--vscode-editor-font-family);
            font-size: var(--vscode-editor-font-size);
            border: 1px solid var(--vscode-panel-border);
            padding: 10px;
            background: var(--vscode-editor-background);
            min-height: 200px;
        }
        label {
            display: flex;
            align-items: center;
            gap: 5px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <h2>Java→なでしこ 変換結果</h2>
    <div id="controls">
        <button id="updateBtn">今すぐ変換</button>
        <label>
            <input type="checkbox" id="autoUpdate" checked>
            自動更新（1秒後）
        </label>
    </div>
    <div id="result">Javaファイルを開いてください</div>
    <script>
        const vscode = acquireVsCodeApi();
        
        // メッセージ受信
        window.addEventListener('message', event => {
            if (event.data.type === 'update') {
                document.getElementById('result').textContent = event.data.content;
            }
        });
        
        // 手動更新ボタン
        document.getElementById('updateBtn').addEventListener('click', () => {
            vscode.postMessage({ type: 'manualUpdate' });
        });
        
        // 自動更新トグル
        document.getElementById('autoUpdate').addEventListener('change', (e) => {
            vscode.postMessage({ 
                type: 'toggleAutoUpdate', 
                value: e.target.checked 
            });
        });
    </script>
</body>
</html>`;
    }
}