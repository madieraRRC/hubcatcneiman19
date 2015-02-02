package hubcat

import com.ning.http.client.Response

import java.util.Date

import org.json4s.{ JArray, JString }
import org.json4s.JsonDSL._

// cli.issues
// cli.userissues
// cli.orgissues

/** http://developer.github.com/v3/issues/#list-issues */
trait Issues { self: Requests =>
  
  def issues = // not found?
    complete[Response](apiHost / "issues")

  def userissues =
    complete[Response](apiHost / "user" / "issues")

  def orgissues(org: String) =
    complete[Response](apiHost / "orgs" / org / "issues")
}

// cli.repo(user, repo).issues.since(time)...
// cli.repo(user, repo).issues.open(title)...
// cli.repo(user, repo).issue(id)
// cli.repo(user, repo).reissue(id).title(...)...
trait RepoIssues { self: RepoRequests  =>

  /** Gihub Issue query filter */
  protected [this]
  case class RepoIssuesFilter(
    user: String,
    repo: String,
    _milestone: String         = "none",
    _state: String             = "open",
    _assignee: Option[String]  = None,
    _creator: Option[String]   = None,
    _mentioned: Option[String] = None,
    _labels: Option[Traversable[String]] = None,
    _sort: String              = "created",
    _order: String             = "desc",
    _since: Option[String]     = None,
    _accept: String = Accept.GithubJson)
    extends Client.Completion[Response] {

    /** http://developer.github.com/v3/issues/#create-an-issue */
    def open(title: String) =
      RepoIssueBuilder(user, repo).title(title)
    
    /** http://developer.github.com/v3/issues/assignees/#list-assignees */
    def assignees =
      complete[Response](apiHost  / "repos" / user / repo / "assignees")

    /** http://developer.github.com/v3/issues/assignees/#check-assignee */
    def assigned(assignee: String) =
      complete[Response](apiHost / "repos" / user / repo / "assignees" / assignee)

    def accepting = new {
      def raw = copy(_accept = Accept.RawJson)
      def text = copy(_accept = Accept.TextJson)
      def html = copy(_accept = Accept.HtmlJson)
      def fullJson = copy(_accept = Accept.FullJson)
    }

    // states (defaults to open)

    def open = copy(_state = "open")
    def closed = copy(_state = "closed")

    // milestones

    def milestone(n: Int) = copy(_milestone = n.toString)
    def noMilestone = copy(_milestone = "none")
    def anyMilestone = copy(_milestone = "*")

    // assignees

    def assignee(login: String) = copy(_assignee = Some(login))
    def unassigned = copy(_assignee = Some("none"))
    def anyAssignee = copy(_assignee = Some("*"))

    def creator(login: String) = copy(_creator = Some(login))
    
    def mentioned(login: String) = copy(_mentioned = Some(login))
    
    def labels(l: Traversable[String]) =
      copy(_labels = Some(l))

    // sorting

    def sortBy = new {
      def created = copy(_sort = "created")
      def updated = copy(_sort = "updated")
      def comments = copy(_sort = "comments")
    }

    // ordering

    def asc = copy(_order = "asc")
    def desc = copy(_order = "desc")

    def since(d: Date) = copy(_since = Some(ISO8601(d)))

    override def apply[T](handler: Client.Handler[T]) =
      request(apiHost / "repos" / user / repo / "issues" <<? query <:< Map("Accept" -> _accept))(handler)

    private def query =
      Map("milestone" -> _milestone,
          "state"     -> _state,
          "sort"      -> _sort,
          "order"     -> _order) ++
      _assignee.map("assignee" -> _) ++
      _creator.map("creator" -> _) ++
      _mentioned.map("mentioned" -> _) ++
      _labels.map("labels" -> _.mkString(","))
  }

  /** Builder for creating or updaing Github issues */
  protected [this]
  case class RepoIssueBuilder(
    user: String,
    repo: String,
    _id: Option[Int]             = None,
    _title: Option[String]       = None,
    _body: Option[String]        = None,
    _assignee: Option[String]    = None,
    _milestone: Option[Int]      = None,
    _labels: Option[Seq[String]] = None,
    _state: Option[String]       = None)
     extends Client.Completion[Response] {

    def title(t: String) = copy(_title = Some(t))

    def body(b: String) = copy(_body = Some(b))

    def assignee(a: String) = copy(_assignee = Some(a))

    def milestone(m: Int) = copy(_milestone = Some(m))

    def labels(ls: Seq[String]) = copy(_labels = Some(ls))

    def close = copy(_state = Some("close"))

    def open = copy(_state = Some("open"))

    override def apply[T](handler: Client.Handler[T]) =
      request(
        _id.fold
        (apiHost.POST / "repos" / user / repo / "issues")
        (apiHost.PATCH / "repos" / user / repo / "issues" / _.toString)
        << payload)(handler)

    def payload =
      json.str(
        ("title" -> _title) ~
        ("body" -> _body) ~
        ("assignee" -> _assignee) ~
        ("milestone" -> _milestone) ~
        ("labels" -> _labels.map(_.toList)) ~
        ("state" -> _state))
  }

  /** Milestone methods */
  protected [this] 
  object Milestones {
    /** http://developer.github.com/v3/issues/milestones/#list-milestones-for-a-repository */
    def find =
      complete[Response](apiHost / "repo" / user / repo / "milestones")

    /** http://developer.github.com/v3/issues/milestones/#get-a-single-milestone */
    def get(num: String) =
      complete[Response](apiHost / "repo" / user / repo / "milestones" / num)

    /** http://developer.github.com/v3/issues/milestones/#update-a-milestone */
    def edit(num: String) =
      complete[Response](apiHost.PATCH / "repo" / user / repo / "milestones" / num)

    /** http://developer.github.com/v3/issues/milestones/#delete-a-milestone */
    def delete(num: String) =
      complete[Response](apiHost.DELETE / "repo" / user / repo / "milestones" / num)

    /** http://developer.github.com/v3/issues/milestones/#create-a-milestone TODO impl this!*/
    def create(num: String) =
      complete[Response](apiHost.POST / "repo" / user / repo / "milestones")
  }

  /** Requests for accessing and creating repo labels */
  protected [this]
  object Labels extends Client.Completion[Response] {
    /** http://developer.github.com/v3/issues/labels/#list-all-labels-for-this-repository */
    def apply[T](hand: Client.Handler[T]) =
      request(apiHost / "repos" / user / repo / "labels")(hand)

    /** http://developer.github.com/v3/issues/labels/#get-a-single-label */
    def get(name: String) =
      complete[Response](apiHost / "repos" / user / repo / "labels" / name)

   /** http://developer.github.com/v3/issues/labels/#create-a-label */
   def create(name: String, color: String) = {
     val payload = json.str(
       ("name" -> name) ~
       ("color" -> color))
     complete[Response](apiHost.POST / "repos" / user / repo / "labels" << payload)
   }

   /** http://developer.github.com/v3/issues/labels/#update-a-label */
   def edit(name: String, color: String) = {
     val payload = json.str(
       ("name" -> name) ~
       ("color" -> color))
     complete[Response](apiHost.PATCH / "repos" / user / repo / "labels" << payload)
   }

   /** http://developer.github.com/v3/issues/labels/#delete-a-label */
   def delete(name: String) =
     complete[Response](apiHost.DELETE / "repos" / user / repo / "labels" / name)
  }

  /** Requests for a specific Github issue */
  protected [this]
  case class Issue(
    id: Int, _accept: String = Accept.GithubJson)
    extends Client.Completion[Response] {

    def accepting = new {
      def raw = copy(_accept = Accept.RawJson)
      def text = copy(_accept = Accept.TextJson)
      def html = copy(_accept = Accept.HtmlJson)
      def fullJson = copy(_accept = Accept.FullJson)
    }

    def labels =
      complete[Response](apiHost / "repos" / user  / repo / "issues" / id.toString / "labels")

    /** http://developer.github.com/v3/issues/labels/#add-labels-to-an-issue */
    def label(labs: String*) = {
      val payload = json.str(jlabs(labs.toList))
      complete[Response](apiHost.POST / "repos" / user / repo / "issues" / id.toString / "labels" << payload)
    }

    /** http://developer.github.com/v3/issues/labels/#replace-all-labels-for-an-issue */
    def relabel(labs: String*) = {
      val payload = json.str(jlabs(labs.toList))
      complete[Response](apiHost.PATCH / "repos" / user / repo / "issues" / id.toString / "labels" << payload)
    }

    private def jlabs(labs: List[String]) =
      JArray(labs.map(new JString(_)))

    /** http://developer.github.com/v3/issues/labels/#remove-all-labels-from-an-issue */
    def delabel = 
      complete[Response](apiHost.DELETE / "repos" / user / repo / "issues" / id.toString / "labels")

    /** http://developer.github.com/v3/issues/labels/#remove-a-label-from-an-issue */
    def delabel(name: String) =
      complete[Response](apiHost.DELETE / "repos" / user / repo / "issues" / id.toString / "labels" / name)

     /** http://developer.github.com/v3/issues/#get-a-single-issue */
    override def apply[T](hand: Client.Handler[T]) =
      request(apiHost / "repos" / user / repo / "issues" / id.toString <:< Map("Accept" -> _accept))(hand)

    def close =
      RepoIssueBuilder(user, repo, _id = Some(id)).close

    /** Github issue comments */
    protected [this]
    object Comments extends Client.Completion[Response] {
      def get(cid: Int) =
        complete[Response](apiHost / "repos" / user / repo / "issues" / id.toString / "comments" / cid.toString)

      def create(body: String) = {
        val payload = json.str(("body" -> body))
        complete[Response](apiHost.POST / "repos" / user / repo / "issues" / id.toString / "comments" << payload)
      }

      def edit(cid: Int, body: String) = {
        val payload = json.str(("body" -> body))
        complete[Response](apiHost.PATCH / "repos" / user / repo / "issues" / "comments" / cid.toString << payload)
      }

      def delete(cid: Int) =
        complete[Response](apiHost.DELETE / "repos" / user / repo / "issues" / "comments" / cid.toString)

      def apply[T](hand: Client.Handler[T]) =
        request(apiHost / "repos" / user / repo / "issues" / id.toString / "comments")(hand)
    }

    def comments = Comments
  }

  def issues =
    RepoIssuesFilter(user, repo)

  def milestones =
    Milestones
  
  def labels =
    Labels

  def issue(id: Int) =
    Issue(id)

  /** http://developer.github.com/v3/issues/#edit-an-issue */
  def reissue(id: Int) =
    RepoIssueBuilder(user, repo, _id = Some(id))  
}