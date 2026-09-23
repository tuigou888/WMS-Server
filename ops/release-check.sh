#!/usr/bin/env sh
set -eu

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "${repo_root}"

if [ "${RELEASE_ALLOW_DIRTY:-}" != "YES" ] && [ -n "$(git status --short)" ]; then
  printf '%s\n' '发布检查失败：工作区存在未提交或未跟踪文件。请整理后提交并创建版本标签。' >&2
  git status --short >&2
  exit 2
fi

git diff --check
(cd wms-server && mvn -q test)
(cd wms-web && npm run build)
(cd wms-miniapp && npm run build:mp-weixin)
(cd wms-shopping-miniapp && npm run build:wechat)
printf '%s\n' '发布前构建与测试检查通过。请在目标环境继续执行真实微信、MySQL 迁移和恢复演练。'
