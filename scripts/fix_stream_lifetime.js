// Rewrites the file-writing section of Downloader.DownloadOneAsync so the output
// stream is closed before the temporary file is moved.
//
// The original kept the FileStream alive until the method returned, but called
// File.Move on the same path before that point. On Windows a move over an open handle
// fails with a sharing violation, which made roughly a quarter of every install fail.
"use strict";

const fs = require("fs");
const path = "Game/Download/Downloader.cs";
let source = fs.readFileSync(path, "utf8");

const lines = [
  '        long written = existing;',
  '        // The stream must be fully closed before the file is moved: on Windows a rename',
  '        // over an open handle fails with a sharing violation, and the dispose that',
  '        // "await using" provides only runs when the method returns, which is too late.',
  '        await using (Stream input = await response.Content.ReadAsStreamAsync(token)',
  '                         .ConfigureAwait(false))',
  '        {',
  '            await using (FileStream output = new(part,',
  '                             existing > 0 ? FileMode.Append : FileMode.Create,',
  '                             FileAccess.Write, FileShare.None, 131072, useAsync: true))',
  '            {',
  '                byte[] buffer = new byte[131072];',
  '                while (true)',
  '                {',
  '                    int read = await input.ReadAsync(buffer, token).ConfigureAwait(false);',
  '                    if (read <= 0)',
  '                    {',
  '                        break;',
  '                    }',
  '                    await output.WriteAsync(buffer.AsMemory(0, read), token).ConfigureAwait(false);',
  '                    written += read;',
  '                    progress.AddBytes(read);',
  '                }',
  '                await output.FlushAsync(token).ConfigureAwait(false);',
  '            }',
  '        }',
  '',
  '        if (expected > 0 && written < expected)',
  '        {',
  '            throw new IOException($"truncated ({written}/{expected})");',
  '        }',
  '',
  '        // Both the response and the file handle are closed here, so the move cannot race.',
  '        File.Move(part, file.Target, overwrite: true);',
  '        Verify(file);',
  '    }',
].join("\n");

const startMarker = "        await using Stream input = await response.Content.ReadAsStreamAsync(token).ConfigureAwait(false);";
const endMarker = "        Verify(file);\n    }";

const start = source.indexOf(startMarker);
if (start < 0) {
  throw new Error("start marker not found; the file changed since this script was written");
}
const end = source.indexOf(endMarker, start);
if (end < 0) {
  throw new Error("end marker not found");
}

source = source.slice(0, start) + lines + source.slice(end + endMarker.length);
fs.writeFileSync(path, source);
console.log("stream lifetime fixed");
