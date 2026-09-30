"""The senior's phone learns an alert was closed elsewhere through GET /seniors/{sync_id}/closed-alerts.

Guards the two properties that matter: it exists on the senior-facing router with no login
(the sync_id is the credential, like every other route the phone calls), and it reports only
ids and a status -- never who closed the alert or why.
"""

from app.api.routes import seniors
from app.schemas.senior import ClosedAlertOut


def test_route_exists_and_is_not_behind_a_login():
    route = next(r for r in seniors.router.routes if r.path.endswith("/{sync_id}/closed-alerts"))
    assert "GET" in route.methods
    dependants = {d.call.__name__ for d in route.dependant.dependencies}
    assert "get_current_user" not in dependants


def test_response_carries_only_id_and_status():
    assert set(ClosedAlertOut.model_fields) == {"sync_id", "status"}
