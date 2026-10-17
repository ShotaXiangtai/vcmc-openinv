# vcmc-openinv

プレイヤーのインベントリおよびエンダーチェストを閲覧・編集するための Minecraft (Paper) プラグインです。オフラインプレイヤーにも対応しており、プレイヤーの死亡時にインベントリ状態をスナップショットとして保存し、後から復元する機能も備えています。

## 概要 (Overview)
- **オンライン/オフライン両対応:** プレイヤーがオフラインの場合でも、インベントリやエンダーチェストの閲覧・編集が可能です。
- **死亡前インベントリの復元:** プレイヤー死亡時のインベントリを自動保存し、コマンドで任意の時点のインベントリに復元できます（ロールバック機能）。
- **リアルタイム同期:** オフライン時に編集・復元されたインベントリは、対象プレイヤーの次回ログイン時に自動的に反映されます。
- **フルカスタマイズ可能なメッセージ:** `config.yml` からすべてのメッセージとカラーコードを変更可能です。

## 機能 (Features)
- プレイヤーインベントリの閲覧・編集 (`/openinv`)
- プレイヤーのエンダーチェストの閲覧・編集 (`/openender`)
- 死亡時のインベントリスナップショット自動保存（保存上限数は設定で変更可能）
- スナップショットからのインベントリ復元 (`/restoreinv`)

## コマンド (Commands)

| コマンド | エイリアス | 説明 | 権限 |
| --- | --- | --- | --- |
| `/openinv <プレイヤー名>` | `/oi` | 指定したプレイヤーのインベントリを開きます。（オフライン可） | `openinv.openinv` |
| `/openender <プレイヤー名>` | `/oe` | 指定したプレイヤーのエンダーチェストを開きます。（オフライン可） | `openinv.openender` |
| `/restoreinv <プレイヤー名> [インデックス] [confirm]` | `/ri` | 指定したプレイヤーの死亡前インベントリを復元します。 | `openinv.restoreinv` |

**`/restoreinv` の使い方:**
- `/restoreinv <プレイヤー名>`: そのプレイヤーの死亡スナップショット一覧（新しい順）とインデックス番号を表示します。
- `/restoreinv <プレイヤー名> <インデックス>`: 指定したインデックスの復元確認メッセージを表示します。
- `/restoreinv <プレイヤー名> <インデックス> confirm`: 実際にインベントリをスナップショットの状態へ復元します。

## パーミッション (Permissions)

| パーミッション | デフォルト | 説明 |
| --- | --- | --- |
| `openinv.openinv` | OP | インベントリを開く・編集する権限 |
| `openinv.openender` | OP | エンダーチェストを開く・編集する権限 |
| `openinv.restoreinv` | OP | 死亡前インベントリを復元する権限 |
| `openinv.*` | OP | vcmc-openinv の全権限 |

## 設定 (Configuration)

`plugins/vcmc-openinv/config.yml` で各種設定が可能です。

```yaml
# プレイヤーごとに保存する死亡スナップショットの最大数
max-death-snapshots: 10

# メッセージ設定（&カラーコード対応）
messages:
  no-permission: '&cこのコマンドを実行する権限がありません。'
  player-not-found: '&cプレイヤー &e%player% &cが見つかりません。'
  # ... その他のメッセージ設定
```

## コード構成 (Code Structure / Architecture)

このプラグインは以下のパッケージおよびクラスで構成されています。

- `net.vcmc.openinv`
  - `OpenInvPlugin.java`: プラグインのメインクラス。コマンドやリスナーの登録、設定やストレージの初期化を行います。
- `net.vcmc.openinv.command`
  - `OpenInvCommand.java`: `/openinv` コマンドの処理。
  - `OpenEnderCommand.java`: `/openender` コマンドの処理。
  - `RestoreInvCommand.java`: `/restoreinv` コマンドの処理（スナップショット一覧表示と復元実行）。
- `net.vcmc.openinv.gui`
  - `InvViewHolder.java` / `EnderViewHolder.java`: インベントリ GUI 操作時のホルダー。これを用いて、インベントリが閉じられた際にデータの保存処理を特定・トリガーします。
- `net.vcmc.openinv.listener`
  - `PlayerListener.java`: イベント監視を担います。
    - `InventoryCloseEvent`: 管理者が `/openinv` や `/openender` で開いたインベントリを閉じたときのデータ保存処理。
    - `PlayerDeathEvent`: プレイヤー死亡時のスナップショット自動保存処理。
    - `PlayerJoinEvent`: オフライン時に編集・復元されたデータのログイン時適用。
    - `PlayerQuitEvent`: プレイヤー退出時の最新データ保存。
- `net.vcmc.openinv.storage`
  - `PlayerDataStorage.java`: `plugins/vcmc-openinv/inventories/` および `enderchests/` に YAML 形式でインベントリデータを保存・読み込みします。オフラインプレイヤーのデータ操作を担います。
  - `DeathStorage.java`: `plugins/vcmc-openinv/deaths/` に死亡時のインベントリスナップショットを YAML 形式で保存します。保存件数が上限（`max-death-snapshots`）を超えた場合の古いファイルの自動削除も行います。
