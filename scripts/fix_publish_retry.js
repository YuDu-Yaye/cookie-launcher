// Replaces the single File.Move with a retrying publish step.
//
// Windows can refuse a rename with UnauthorizedAccessException or a sharing violation
// for reasons unrelated to our own handles: antivirus scanners, the search indexer and
// cloud-sync clients all open freshly written files briefly. A launcher that gives up on
// the first refusal fails installs on machines that are otherwise perfectly healthy.
//
// The publish step below retries with a short backoff, then falls back to
// copy-then-delete, which succeeds in the case that rename cannot.
"use strict";

const fs = require("fs");
const path = "Game/Download/Downloader.cs";
let source = fs.readFileSync(path, "utf8");

// 1. Route every publish through the new helper.
source = source.replace(
  "        // Both the response and the file handle are closed here, so the move cannot race.\n"
  + "        File.Move(part, file.Target, overwrite: true);\n"
  + "        Verify(file);",
  "        // Both the response and the file handle are closed here.\n"
  + "        Publish(part, file.Target);\n"
  + "        Verify(file);",
);

// The 416 path also publishes.
source = source.replace(
  "            File.Move(part, file.Target, overwrite: true);\n"
  + "            Verify(file);\n"
  + "            return;",
  "            Publish(part, file.Target);\n"
  + "            Verify(file);\n"
  + "            return;",
);

// 2. Add the helper right before the temporary cleanup routine.
const helper = [
  '    /// <summary>',
  '    /// Moves a finished temporary file into place, tolerating transient Windows refusals.',
  '    /// </summary>',
  '    /// <remarks>',
  '    /// A rename can fail with UnauthorizedAccessException or a sharing violation even',
  '    /// though this process holds no handle on the file, because antivirus scanners, the',
  '    /// search indexer and cloud-sync clients open newly written files for a moment. The',
  '    /// retry loop rides out that window; the copy fallback succeeds where rename cannot.',
  '    /// </remarks>',
  '    private static void Publish(string temporary, string target)',
  '    {',
  '        Exception? last = null;',
  '        for (int attempt = 0; attempt < 6; attempt++)',
  '        {',
  '            try',
  '            {',
  '                File.Move(temporary, target, overwrite: true);',
  '                return;',
  '            }',
  '            catch (Exception error) when (error is IOException or UnauthorizedAccessException)',
  '            {',
  '                last = error;',
  '                Thread.Sleep(40 * (attempt + 1));',
  '            }',
  '        }',
  '',
  '        // Rename keeps being refused: copy over the target instead.',
  '        try',
  '        {',
  '            File.Copy(temporary, target, overwrite: true);',
  '            File.Delete(temporary);',
  '            return;',
  '        }',
  '        catch (Exception error) when (error is IOException or UnauthorizedAccessException)',
  '        {',
  '            last = error;',
  '        }',
  '',
  '        throw new IOException("cannot publish " + Path.GetFileName(target), last);',
  '    }',
  '',
  '',
].join("\n");

source = source.replace(
  "    /// <summary>Removes stale temporary files left by failed attempts.</summary>",
  helper + "    /// <summary>Removes stale temporary files left by failed attempts.</summary>",
);

fs.writeFileSync(path, source);
console.log("publish step hardened");
