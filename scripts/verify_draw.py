#!/usr/bin/env python3
"""Verifica, de forma independente, a prova de um sorteio da Sorteios API.

Uso:
    python scripts/verify_draw.py https://<api>/api/raffles/<id>/proof
    python scripts/verify_draw.py prova.json

Só usa a biblioteca padrão. Sai com código 0 se a prova confere, 1 caso contrário.
"""
import hashlib
import json
import sys
import urllib.request


def load(source):
    if source.startswith(("http://", "https://")):
        with urllib.request.urlopen(source, timeout=120) as response:
            return json.load(response)
    with open(source, encoding="utf-8") as file:
        return json.load(file)


def verify(proof):
    errors = []
    seed = bytes.fromhex(proof["seed"])

    if hashlib.sha256(seed).hexdigest() != proof["seedHash"]:
        errors.append("SHA-256(seed) não bate com o seedHash publicado antes das vendas")

    tickets = sorted(proof["tickets"], key=lambda t: t["number"])
    line = ",".join(f'{t["number"]}:{t["userId"]}' for t in tickets)
    digest = hashlib.sha256(line.encode("utf-8")).hexdigest()
    if digest != proof["ticketsDigest"]:
        errors.append("a lista de bilhetes (número:comprador) não bate com o ticketsDigest do sorteio")

    h = hashlib.sha256(seed + digest.encode("utf-8")).digest()
    index = int.from_bytes(h[:8], "big") % len(tickets)
    winning = tickets[index]["number"]
    if index != proof["winnerIndex"] or winning != proof["winningNumber"]:
        errors.append(f"vencedor recalculado é o número {winning} (posição {index}), "
                      f"mas a API informou {proof['winningNumber']} (posição {proof['winnerIndex']})")
    return winning, errors


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    winning, errors = verify(load(sys.argv[1]))
    if errors:
        for error in errors:
            print("FALHOU:", error)
        return 1
    print(f"OK: o número vencedor {winning} confere com a semente e com os bilhetes vendidos")
    return 0


if __name__ == "__main__":
    sys.exit(main())
