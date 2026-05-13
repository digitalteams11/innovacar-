@echo off
set ELECTRON_RUN_AS_NODE=0
npm run build
set NODE_ENV=production
.\node_modules\electron\dist\electron.exe .
