import electron from 'electron';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const child=spawn(electron,[fileURLToPath(new URL('./f1-main.js',import.meta.url))],{stdio:'inherit',env:process.env});
child.on('exit',code=>{process.exitCode=code??1;});
for(const signal of ['SIGINT','SIGTERM'])process.once(signal,()=>child.kill(signal));
